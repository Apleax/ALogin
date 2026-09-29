package xyz.apleax.alogin.viaproxy;

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
 * @param enabled                是否启用（false = 完全透传）
 * @param secret                 与 ALogin 端 Premium.assertion.secret 一致的共享密钥
 * @param channel                与 ALogin 端 Premium.assertion.channel 一致的消息通道
 * @param aloginUrl              ALogin 服务地址，用于拉取已绑定正版名单（留空 = 不校验）
 * @param boundListRefreshSeconds 名单刷新间隔（秒）
 * @param verifyTimeoutMs        加密应答超时（毫秒），超时按离线处理
 * @param fakePremiumUuid        仅测试用：跳过校验，固定用此 UUID 生成断言（生产留空）
 * @param fakePremiumName        测试模式使用的名称
 */
public record PluginConfig(boolean enabled, String secret, String channel, String aloginUrl,
                           long boundListRefreshSeconds, long verifyTimeoutMs,
                           String fakePremiumUuid, String fakePremiumName) {
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
            fake-premium-uuid=
            fake-premium-name=FakePremium
            """;

    public static PluginConfig load(final File dataFolder) {
        final Path file = dataFolder.toPath().resolve("config.properties");
        try {
            if (!Files.exists(file)) Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
            final Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return new PluginConfig(
                    Boolean.parseBoolean(properties.getProperty("enabled", "true").trim()),
                    properties.getProperty("secret", "").trim(),
                    properties.getProperty("channel", "alogin:premium").trim(),
                    parseAloginUrl(properties),
                    Long.parseLong(properties.getProperty("bound-list-refresh-seconds", "15").trim()),
                    Long.parseLong(properties.getProperty("verify-timeout-ms", "5000").trim()),
                    properties.getProperty("fake-premium-uuid", "").trim(),
                    properties.getProperty("fake-premium-name", "FakePremium").trim());
        } catch (Exception e) {
            Logger.LOGGER.error("[ALoginPremium] 读取配置失败，按禁用处理", e);
            return new PluginConfig(false, "", "alogin:premium", "", 15, 5000, "", "FakePremium");
        }
    }

    /** 解析 ALogin 地址: 必须以 http:// 或 https:// 开头, 非法则按未配置处理 */
    private static String parseAloginUrl(Properties properties) {
        String value = properties.getProperty("alogin-url", "").trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            Logger.LOGGER.error("[ALoginPremium] 'alogin-url' 格式非法 (应为 http://主机:端口, 当前为 '" + value
                    + "'), 按未配置处理");
            return "";
        }
        return value;
    }
}
