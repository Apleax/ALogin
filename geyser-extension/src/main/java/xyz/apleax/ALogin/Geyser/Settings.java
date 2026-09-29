package xyz.apleax.ALogin.Geyser;

import org.geysermc.geyser.api.extension.ExtensionLogger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 扩展配置: 首次使用时在扩展数据目录生成 config.properties
 *
 * @author Apleax
 */
public record Settings(HostPort bedrockAddress, HostPort bedrockTarget, HostPort transferTarget, long cookieTtlMillis,
                       boolean formLogin, boolean xuidAutologin, String aloginUrl, String cookieKey, Header aloginHeader) {
    public static final String FILE_NAME = "config.properties";
    /** 基岩地址省略端口时的默认端口 */
    private static final int BEDROCK_DEFAULT_PORT = 19132;
    /** Java 地址(Velocity 等)省略端口时的默认端口 */
    private static final int JAVA_DEFAULT_PORT = 25565;
    private static final long DEFAULT_COOKIE_TTL_MILLIS = 60_000L;
    private static final String DEFAULT_CONTENT = """
            # 基岩客户端重连用的地址, 即本 Geyser 的基岩地址, 例如 play.example.com:19132 (省略端口时默认 19132)
            # 留空时使用玩家本次进服时填写的地址
            # 填其他基岩服务器地址时客户端会直接连过去(不再由本 Geyser 接管)
            bedrock-address=
            # 仅基岩版生效: 重连回本 Geyser 后真正要连接的下游服务器, 例如 localhost:25565 (省略端口时默认 25565)
            # 留空时与 Java 版一致, 使用 transfer 包中的目标地址
            bedrock-target=
            # 只接管发往该地址的 transfer, 例如代理的 localhost:25566 (省略端口时默认 25565)
            # 需与登录服 transfer 配置一致, 主机名不区分大小写, 端口必须相同, 否则 transfer 不会被接管
            # 留空时接管所有 transfer
            transfer-target=
            # 暂存 cookie 的有效期, 单位秒
            cookie-ttl-seconds=60
            # 是否启用基岩版原生表单登录(在等待服里弹表单, 由本扩展直接向 ALogin 校验并交接)
            # 启用后建议同时配好 transfer-target 或 bedrock-target, 以及 alogin-url
            form-login=false
            # 基岩版免密自动登录: 玩家首次用密码登录(表单或 /l)后自动绑定其 XUID, 之后进服直接免密交接, 不再弹表单
            # 需要与 ALogin 端 Premium.assertion.secret 一致的密钥(见 alogin-header-*), 且 alogin-url 指向 ALogin API
            # 设为 false 则关闭免密(未绑定时照常弹表单)
            xuid-autologin=true
            # ALogin 服务地址, 表单登录与免密校验用, 例如 https://apleax.xyz 或 http://127.0.0.1:8080
            # 校验是同步等待, 建议填本机或内网地址
            alogin-url=http://127.0.0.1:8080
            # cookie 键名, 需与登录服/代理的 cookie-key 保持一致
            cookie-key=alogin
            # 调用 ALogin 时额外附带的请求头, 用于给雷池等 WAF 免校验放行, 同时作为内部接口的共享密钥
            # (请求头名与值要和白名单规则、Premium.assertion.secret 一致, 例如 X-ALogin-Key = 那个密钥)
            # 两项都填才发送, 只填一项会报错并忽略; 不要填 Content-Type 或 Accept
            alogin-header-name=
            alogin-header-value=
            """;

    /**
     * 读取(必要时生成)配置文件
     */
    public static Settings load(Path dataFolder, ExtensionLogger logger) {
        Path path = dataFolder.resolve(FILE_NAME);
        Properties properties = new Properties();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(dataFolder);
                Files.writeString(path, DEFAULT_CONTENT);
                logger.info("Generated " + FILE_NAME + " in " + dataFolder);
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
        } catch (Exception e) {
            logger.error("Failed to read " + FILE_NAME + ", fall back to the default values", e);
            return new Settings(null, null, null, DEFAULT_COOKIE_TTL_MILLIS, false, true, "", "alogin", null);
        }
        String aloginUrl = properties.getProperty("alogin-url", "").trim();
        if (!aloginUrl.isEmpty() && !aloginUrl.startsWith("http://") && !aloginUrl.startsWith("https://")) {
            logger.error("Ignored the invalid 'alogin-url' value '" + aloginUrl + "', expected http://host:port");
            aloginUrl = "";
        }
        Settings settings = new Settings(
                parse(properties, "bedrock-address", BEDROCK_DEFAULT_PORT, logger),
                parse(properties, "bedrock-target", JAVA_DEFAULT_PORT, logger),
                parse(properties, "transfer-target", JAVA_DEFAULT_PORT, logger),
                parseCookieTtl(properties, logger),
                parseFlag(properties, "form-login", false, logger),
                parseFlag(properties, "xuid-autologin", true, logger),
                aloginUrl,
                properties.getProperty("cookie-key", "alogin").trim(),
                parseHeader(properties, logger));
        logger.info("bedrock-address=" + settings.bedrockAddress()
                + ", bedrock-target=" + settings.bedrockTarget()
                + ", transfer-target=" + settings.transferTarget()
                + ", cookie-ttl-seconds=" + settings.cookieTtlMillis() / 1000L
                + ", form-login=" + settings.formLogin()
                + ", xuid-autologin=" + settings.xuidAutologin()
                + ", alogin-url=" + settings.aloginUrl()
                + ", cookie-key=" + settings.cookieKey()
                + ", alogin-header=" + (settings.aloginHeader() == null ? "none" : settings.aloginHeader().name()));
        return settings;
    }

    /** 解析 host[:port]: 留空或非法返回 null(禁用) */
    private static HostPort parse(Properties properties, String key, int defaultPort, ExtensionLogger logger) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            HostPort parsed = HostPort.parse(value, defaultPort);
            if (parsed.host().isBlank() || parsed.port() < 1 || parsed.port() > 65535) {
                throw new IllegalArgumentException("invalid host or port");
            }
            return parsed;
        } catch (RuntimeException e) {
            logger.error("Ignored the invalid '" + key + "' value '" + value + "', expected host[:port]");
            return null;
        }
    }

    /** 解析 cookie 暂存秒数, 非法值回退默认 */
    private static long parseCookieTtl(Properties properties, ExtensionLogger logger) {
        String value = properties.getProperty("cookie-ttl-seconds");
        if (value == null || value.isBlank()) {
            return DEFAULT_COOKIE_TTL_MILLIS;
        }
        try {
            return Math.multiplyExact(Long.parseLong(value.trim()), 1000L);
        } catch (ArithmeticException | NumberFormatException e) {
            logger.error("Ignored the invalid 'cookie-ttl-seconds' value '" + value + "', fall back to 60");
            return DEFAULT_COOKIE_TTL_MILLIS;
        }
    }

    /** 解析开关: 只认 true/false, 非法值回退默认 */
    private static boolean parseFlag(Properties properties, String key, boolean defaultValue, ExtensionLogger logger) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        logger.error("Ignored the invalid '" + key + "' value '" + value + "', expected true or false");
        return defaultValue;
    }

    /** 解析额外请求头: 两项都留空返回 null(不发送); 只填一项或值非法则按 null 处理 */
    private static Header parseHeader(Properties properties, ExtensionLogger logger) {
        String name = properties.getProperty("alogin-header-name", "").trim();
        String value = properties.getProperty("alogin-header-value", "").trim();
        if (name.isEmpty() && value.isEmpty()) {
            return null;
        }
        if (name.isEmpty() || value.isEmpty()) {
            logger.error("'alogin-header-name' and 'alogin-header-value' must be set together, ignored");
            return null;
        }
        return Header.parse(name, value, logger);
    }

    /**
     * 附加到 ALogin 请求的请求头（给 WAF 放行用）
     */
    public record Header(String name, String value) {
        /** 校验并构造: 非法返回 null 并记日志(不记值) */
        static Header parse(String name, String value, ExtensionLogger logger) {
            if (!name.matches("[A-Za-z0-9._-]+")) {
                logger.error("Ignored 'alogin-header-name': only letters, digits, '.', '-' and '_' are allowed");
                return null;
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c > 0x7E) {
                    logger.error("Ignored 'alogin-header-value': only printable ASCII characters are allowed");
                    return null;
                }
            }
            return new Header(name, value);
        }
    }

    /**
     * 主机与端口, 支持省略端口
     */
    public record HostPort(String host, int port) {
        /** 解析 host[:port]: 值留空返回 null; 没有冒号时使用 defaultPort */
        public static HostPort parse(String value, int defaultPort) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.trim();
            int index = trimmed.lastIndexOf(':');
            if (index < 0) {
                return new HostPort(trimmed, defaultPort);
            }
            return new HostPort(trimmed.substring(0, index), Integer.parseInt(trimmed.substring(index + 1)));
        }

        /** 与 host:port 比较, 主机名不区分大小写 */
        public boolean matches(String host, int port) {
            return this.port == port && this.host.equalsIgnoreCase(host);
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
