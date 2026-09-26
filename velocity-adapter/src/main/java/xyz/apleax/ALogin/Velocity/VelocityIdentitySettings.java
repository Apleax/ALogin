package xyz.apleax.ALogin.Velocity;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * LibreLogin/Velocity 适配器配置。
 *
 * @author Apleax
 */
public record VelocityIdentitySettings(
        boolean enabled,
        String issuer,
        String audience,
        String cookieKey,
        String javaSharedSecret,
        long assertionTtlMillis
) {
    public static final String FILE_NAME = "config.properties";
    private static final String DEFAULT_CONTENT = """
            # 是否向已由 LibreLogin 确认的 Java 正版玩家签发 ALogin 身份断言
            enabled=false
            issuer=ALogin
            audience=ALogin
            cookie-key=alogin:identity
            # 必须与 ALogin 的 identity.java-shared-secret 完全一致，至少 32 个 UTF-8 字节
            java-shared-secret=
            assertion-ttl-seconds=60
            """;

    public static VelocityIdentitySettings load(Path dataFolder, Logger logger) {
        Path path = dataFolder.resolve(FILE_NAME);
        Properties properties = new Properties();
        try {
            Files.createDirectories(dataFolder);
            if (Files.notExists(path)) Files.writeString(path, DEFAULT_CONTENT);
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
        } catch (Exception exception) {
            logger.error("Failed to load {}", path, exception);
            return disabled();
        }

        String secret = properties.getProperty("java-shared-secret", "").trim();
        boolean enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"))
                && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
        long ttl = parseTtl(properties.getProperty("assertion-ttl-seconds", "60"), logger);
        VelocityIdentitySettings settings = new VelocityIdentitySettings(
                enabled,
                text(properties.getProperty("issuer"), "ALogin"),
                text(properties.getProperty("audience"), "ALogin"),
                text(properties.getProperty("cookie-key"), "alogin:identity"),
                secret,
                ttl);
        if (!enabled && Boolean.parseBoolean(properties.getProperty("enabled", "false"))) {
            logger.warn("Java identity adapter remains disabled because java-shared-secret is shorter than 32 UTF-8 bytes");
        }
        return settings;
    }

    private static VelocityIdentitySettings disabled() {
        return new VelocityIdentitySettings(false, "ALogin", "ALogin", "alogin:identity", "", 60_000L);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long parseTtl(String value, Logger logger) {
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException();
            return seconds * 1000L;
        } catch (RuntimeException exception) {
            logger.warn("Invalid assertion-ttl-seconds '{}', using 60", value);
            return 60_000L;
        }
    }
}
