package xyz.apleax.ALogin.viaproxy;

import net.raphimc.viaproxy.util.logging.Logger;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 插件配置（plugins/ALoginPremium/config.properties）
 *
 * @param enabled                 是否启用（false = 完全透传）
 * @param secret                  与 ALogin 端 Premium.assertion.secret 一致的共享密钥
 * @param channel                 与 ALogin 端 Premium.assertion.channel 一致的消息通道
 * @param aloginUrl               ALogin 服务地址，用于拉取已绑定正版名单（留空 = 不校验）
 * @param boundListRefreshSeconds 名单刷新间隔（秒）
 * @param verifyTimeoutMs         加密应答超时（毫秒），超时按离线处理
 * @param fakePremiumUuid         仅测试用：跳过校验，固定用此 UUID 生成断言（生产留空）
 * @param fakePremiumName         测试模式使用的名称
 * @param fakePremiumAllowInProd  显式允许测试模式（防止 fake-premium-uuid 误留在生产配置）
 */
public record PluginConfig(boolean enabled, String secret, String channel, String aloginUrl,
                           long boundListRefreshSeconds, long verifyTimeoutMs,
                           String fakePremiumUuid, String fakePremiumName, boolean fakePremiumAllowInProd) {
    private static final String DEFAULT_FILE = """
            # ALogin 正版免密插件配置
            # 是否启用正版校验（false = 完全透传，行为等同未安装插件）
            enabled=true
            # 与 ALogin 端 Premium.assertion.secret 一致的共享密钥（openssl rand -hex 32 生成）
            # 同时作为拉取已绑定正版名单的请求头 X-ALogin-Key
            secret=
            # 与 ALogin 端 Premium.assertion.channel 一致的登录插件消息通道
            channel=alogin:premium
            # ALogin 服务地址（拉取"已绑定正版 UUID 名单"用）：只有名单内的连接才会被发起正版校验
            # 建议填本机/内网地址，例如 http://127.0.0.1:8080；留空 = 所有连接完全透传
            alogin-url=http://127.0.0.1:8080
            # 名单刷新间隔（秒）：网页刚绑定完的玩家需要等下一次刷新后才会被自动识别
            bound-list-refresh-seconds=15
            # 等待客户端加密应答的超时（毫秒），超时按离线处理（不踢人）
            verify-timeout-ms=5000
            # !! 仅测试用 !! 设置后跳过正版校验，固定用该正版 UUID/名称生成断言（生产必须留空）
            # 设置 fake-premium-uuid 后还必须显式把 fake-premium-allow-in-prod 设为 true，否则插件拒绝启用
            fake-premium-uuid=
            fake-premium-name=FakePremium
            fake-premium-allow-in-prod=false
            """;

    private static final String DEFAULT_PARSE_ERROR = "[ALoginPremium] '{}' 取值非法（'{}'），按默认 {} 处理";

    public static PluginConfig load(final File dataFolder) {
        final Path file = dataFolder.toPath().resolve("config.properties");
        final Properties properties = new Properties();
        try {
            if (!Files.exists(file)) Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (Exception e) {
            Logger.LOGGER.error("[ALoginPremium] 读取配置失败，按禁用处理", e);
            return new PluginConfig(false, "", "alogin:premium", "", 15, 5000, "", "FakePremium", false);
        }
        final boolean enabled = parseBool(properties, "enabled", true);
        final String secret = properties.getProperty("secret", "").trim();
        final String channel = properties.getProperty("channel", "alogin:premium").trim();
        final String aloginUrl = parseAloginUrl(properties);
        final long refreshSeconds = parseLong(properties, "bound-list-refresh-seconds", 15L);
        final long verifyTimeoutMs = Math.max(1000L, parseLong(properties, "verify-timeout-ms", 5000L));
        String fakePremiumUuid = properties.getProperty("fake-premium-uuid", "").trim();
        final boolean fakePremiumAllowInProd = parseBool(properties, "fake-premium-allow-in-prod", false);
        final String fakePremiumName = properties.getProperty("fake-premium-name", "FakePremium").trim();
        if (!fakePremiumUuid.isBlank()) try {
            parseUuid(fakePremiumUuid);
        } catch (IllegalArgumentException e) {
            Logger.LOGGER.error("[ALoginPremium] 'fake-premium-uuid' 不是合法 UUID，按未配置处理");
            fakePremiumUuid = "";
        }
        if (!secret.isEmpty() && secret.length() < 32)
            Logger.LOGGER.warn("[ALoginPremium] secret 长度不足 32 字符，弱密钥易被暴力破解，建议用 openssl rand -hex 32 生成");
        return new PluginConfig(enabled, secret, channel, aloginUrl, refreshSeconds, verifyTimeoutMs,
                fakePremiumUuid, fakePremiumName, fakePremiumAllowInProd);
    }

    /**
     * 解析开关：只认 true/false，非法值记日志并回退默认
     */
    private static boolean parseBool(final Properties properties, final String key, final boolean defaultValue) {
        final String value = properties.getProperty(key, String.valueOf(defaultValue)).trim();
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        parseErrorPrint(key, value, defaultValue);
        return defaultValue;
    }

    /**
     * 解析长整型：非法值记日志并回退默认
     */
    private static long parseLong(final Properties properties, final String key, final long defaultValue) {
        final String value = properties.getProperty(key, String.valueOf(defaultValue)).trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            parseErrorPrint(key, value, defaultValue);
            return defaultValue;
        }
    }

    private static void parseErrorPrint(String key, String value, Object defaultValue) {
        Logger.LOGGER.error(DEFAULT_PARSE_ERROR, key, value, defaultValue);
    }

    /**
     * 解析 ALogin 地址: 必须以 http:// 或 https:// 开头且主机非空, 非法则按未配置处理
     */
    private static String parseAloginUrl(final Properties properties) {
        String value = properties.getProperty("alogin-url", "").trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            Logger.LOGGER.error("[ALoginPremium] 'alogin-url' 格式非法 (应为 http://主机:端口, 当前为 '{}'), 按未配置处理", value);
            return "";
        }
        try {
            if (java.net.URI.create(value).getHost() == null) {
                Logger.LOGGER.error("[ALoginPremium] 'alogin-url' 缺少主机名 ('{}'), 按未配置处理", value);
                return "";
            }
        } catch (IllegalArgumentException e) {
            Logger.LOGGER.error("[ALoginPremium] 'alogin-url' 格式非法 ('{}'), 按未配置处理", value);
            return "";
        }
        return value;
    }

    /**
     * 解析 UUID（支持带连字符与 32 位紧凑格式）
     */
    static java.util.UUID parseUuid(final String raw) {
        final String value = raw.trim();
        return java.util.UUID.fromString(value.contains("-") ? value : value.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
