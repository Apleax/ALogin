package xyz.apleax.ALogin.Geyser;

import org.geysermc.geyser.api.extension.ExtensionLogger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * ALogin Transfer 与外部身份断言配置。
 *
 * <p>共享密钥留空时，身份断言功能保持关闭；改完配置后需要重启 Geyser 才会生效。</p>
 *
 * @author Apleax
 */
public record Settings(
        HostPort bedrockAddress,
        HostPort bedrockTarget,
        HostPort transferTarget,
        long cookieTtlMillis,
        boolean identityEnabled,
        String identityCookieKey,
        String identityIssuer,
        String identityAudience,
        String identitySharedSecret,
        long identityAssertionTtlMillis
) {
    public static final String FILE_NAME = "config.properties";
    private static final int BEDROCK_DEFAULT_PORT = 19132;
    private static final int JAVA_DEFAULT_PORT = 25565;
    private static final long DEFAULT_COOKIE_TTL_MILLIS = 60_000L;
    private static final long DEFAULT_IDENTITY_ASSERTION_TTL_MILLIS = 60_000L;
    private static final String DEFAULT_IDENTITY_COOKIE_KEY = "alogin:identity";
    private static final String DEFAULT_IDENTITY_ISSUER = "ALogin";
    private static final String DEFAULT_IDENTITY_AUDIENCE = "ALogin";
    private static final String DEFAULT_CONTENT = """
            # 基岩客户端重连用的地址，例如 play.example.com:19132
            # 留空时使用玩家本次进服时填写的地址
            bedrock-address=
            # 仅基岩版生效：重连回本 Geyser 后真正要连接的下游服务器
            # 留空时与 Java 版一致，使用 transfer 包中的目标地址
            bedrock-target=
            # 只接管发往该地址的 transfer；留空时接管所有 transfer
            transfer-target=
            # 暂存 cookie 的有效期，单位秒
            cookie-ttl-seconds=60

            # 外部身份断言：与 ALogin 的 identity 配置保持一致
            identity-enabled=false
            identity-cookie-key=alogin:identity
            identity-issuer=ALogin
            identity-audience=ALogin
            identity-shared-secret=
            identity-assertion-ttl-seconds=60
            """;

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
        } catch (Exception exception) {
            logger.error("Failed to read " + FILE_NAME + ", fall back to the default values", exception);
            return defaults();
        }

        HostPort bedrockAddress = parse(properties, "bedrock-address", BEDROCK_DEFAULT_PORT, logger);
        HostPort bedrockTarget = parse(properties, "bedrock-target", JAVA_DEFAULT_PORT, logger);
        HostPort transferTarget = parse(properties, "transfer-target", JAVA_DEFAULT_PORT, logger);
        long cookieTtl = parseSeconds(properties, "cookie-ttl-seconds", 60, logger);
        boolean identityEnabled = Boolean.parseBoolean(properties.getProperty("identity-enabled", "false"));
        String identityCookieKey = textOrDefault(properties, "identity-cookie-key", DEFAULT_IDENTITY_COOKIE_KEY);
        String identityIssuer = textOrDefault(properties, "identity-issuer", DEFAULT_IDENTITY_ISSUER);
        String identityAudience = textOrDefault(properties, "identity-audience", DEFAULT_IDENTITY_AUDIENCE);
        String identitySharedSecret = properties.getProperty("identity-shared-secret", "").trim();
        long identityAssertionTtl = parseSeconds(properties, "identity-assertion-ttl-seconds", 60, logger);
        if (identitySharedSecret.length() < 16) identityEnabled = false;

        Settings settings = new Settings(
                bedrockAddress,
                bedrockTarget,
                transferTarget,
                cookieTtl,
                identityEnabled,
                identityCookieKey,
                identityIssuer,
                identityAudience,
                identitySharedSecret,
                identityAssertionTtl);
        logger.info("bedrock-address=" + settings.bedrockAddress()
                + ", bedrock-target=" + settings.bedrockTarget()
                + ", transfer-target=" + settings.transferTarget()
                + ", cookie-ttl-seconds=" + settings.cookieTtlMillis() / 1000L
                + ", identity-enabled=" + settings.identityEnabled());
        return settings;
    }

    private static Settings defaults() {
        return new Settings(null, null, null, DEFAULT_COOKIE_TTL_MILLIS, false,
                DEFAULT_IDENTITY_COOKIE_KEY, DEFAULT_IDENTITY_ISSUER, DEFAULT_IDENTITY_AUDIENCE,
                "", DEFAULT_IDENTITY_ASSERTION_TTL_MILLIS);
    }

    private static String textOrDefault(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static HostPort parse(Properties properties, String key, int defaultPort, ExtensionLogger logger) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return null;
        try {
            HostPort parsed = HostPort.parse(value, defaultPort);
            if (parsed.host().isBlank() || parsed.port() < 1 || parsed.port() > 65535) {
                throw new IllegalArgumentException("invalid host or port");
            }
            return parsed;
        } catch (RuntimeException exception) {
            logger.error("Ignored the invalid '" + key + "' value '" + value + "', expected host[:port]");
            return null;
        }
    }

    private static long parseSeconds(Properties properties, String key, long fallback, ExtensionLogger logger) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return fallback * 1000L;
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException();
            return Math.multiplyExact(seconds, 1000L);
        } catch (RuntimeException exception) {
            logger.error("Ignored the invalid '" + key + "' value '" + value + "'");
            return fallback * 1000L;
        }
    }

    public record HostPort(String host, int port) {
        public static HostPort parse(String value, int defaultPort) {
            if (value == null || value.isBlank()) return null;
            String trimmed = value.trim();
            int index = trimmed.lastIndexOf(':');
            if (index < 0) return new HostPort(trimmed, defaultPort);
            return new HostPort(trimmed.substring(0, index), Integer.parseInt(trimmed.substring(index + 1)));
        }

        public boolean matches(String host, int port) {
            return this.port == port && this.host.equalsIgnoreCase(host);
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
