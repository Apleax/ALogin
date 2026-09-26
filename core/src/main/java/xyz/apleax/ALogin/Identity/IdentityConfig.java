package xyz.apleax.ALogin.Identity;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Managed;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 外部身份信任边界配置。
 *
 * <p>Java 与 Bedrock 使用彼此隔离的密钥。默认关闭；生产环境必须通过外部配置提供至少 32 字节密钥，
 * 不能把密钥提交到仓库。</p>
 *
 * @author Apleax
 */
@Managed(name = "IdentityConfig", index = -100)
public final class IdentityConfig {
    private static final String DEFAULT_ISSUER = "ALogin";
    private static final String DEFAULT_AUDIENCE = "ALogin";
    private static final String DEFAULT_COOKIE_KEY = "alogin:identity";
    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(15);
    private static final Duration DEFAULT_ASSERTION_MAX_LIFETIME = Duration.ofSeconds(60);

    private final boolean enabled;
    private final String issuer;
    private final String audience;
    private final String javaSharedSecret;
    private final String bedrockSharedSecret;
    private final String cookieKey;
    private final Duration clockSkew;
    private final Duration assertionMaxLifetime;

    public IdentityConfig() {
        this.enabled = Solon.cfg().getBool("identity.enabled", false);
        this.issuer = text(Solon.cfg().get("identity.issuer", DEFAULT_ISSUER), DEFAULT_ISSUER);
        this.audience = text(Solon.cfg().get("identity.audience", DEFAULT_AUDIENCE), DEFAULT_AUDIENCE);
        this.javaSharedSecret = text(Solon.cfg().get("identity.java-shared-secret", ""), "");
        this.bedrockSharedSecret = text(Solon.cfg().get("identity.bedrock-shared-secret", ""), "");
        this.cookieKey = text(Solon.cfg().get("identity.cookie-key", DEFAULT_COOKIE_KEY), DEFAULT_COOKIE_KEY);
        this.clockSkew = nonNegativeSeconds(
                Solon.cfg().getInt("identity.clock-skew-seconds", 15), DEFAULT_CLOCK_SKEW);
        this.assertionMaxLifetime = boundedLifetime(
                Solon.cfg().getInt("identity.assertion-max-seconds", 60), DEFAULT_ASSERTION_MAX_LIFETIME);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Duration nonNegativeSeconds(int value, Duration fallback) {
        if (value < 0) return fallback;
        return Duration.ofSeconds(value);
    }

    private static Duration boundedLifetime(int value, Duration fallback) {
        if (value < 1) return fallback;
        return Duration.ofSeconds(Math.min(value, Duration.ofHours(1).toSeconds()));
    }

    public boolean enabled() {
        return enabled && !issuer.isBlank() && !audience.isBlank() && !cookieKey.isBlank()
                && (enabledFor(ExternalIdentityProvider.JAVA_MOJANG)
                || enabledFor(ExternalIdentityProvider.BEDROCK_XUID));
    }

    public boolean enabledFor(ExternalIdentityProvider provider) {
        return provider != null && secretFor(provider) != null;
    }

    public String secretFor(ExternalIdentityProvider provider) {
        if (provider == ExternalIdentityProvider.JAVA_MOJANG) {
            return validSecret(javaSharedSecret) ? javaSharedSecret : null;
        }
        if (provider == ExternalIdentityProvider.BEDROCK_XUID) {
            return validSecret(bedrockSharedSecret) ? bedrockSharedSecret : null;
        }
        return null;
    }

    private static boolean validSecret(String secret) {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public String javaSharedSecret() {
        return javaSharedSecret;
    }

    public String bedrockSharedSecret() {
        return bedrockSharedSecret;
    }

    public String cookieKey() {
        return cookieKey;
    }

    public Duration clockSkew() {
        return clockSkew;
    }

    public Duration assertionMaxLifetime() {
        return assertionMaxLifetime;
    }
}
