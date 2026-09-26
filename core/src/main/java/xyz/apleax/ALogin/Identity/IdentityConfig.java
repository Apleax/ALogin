package xyz.apleax.ALogin.Identity;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Managed;

import java.time.Duration;

/**
 * 外部身份信任边界配置。
 *
 * <p>默认关闭。生产环境必须通过外部配置提供共享密钥，不能把密钥提交到仓库。</p>
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
    private final String sharedSecret;
    private final String cookieKey;
    private final Duration clockSkew;
    private final Duration assertionMaxLifetime;

    public IdentityConfig() {
        this.enabled = Solon.cfg().getBool("identity.enabled", false);
        this.issuer = text(Solon.cfg().get("identity.issuer", DEFAULT_ISSUER), DEFAULT_ISSUER);
        this.audience = text(Solon.cfg().get("identity.audience", DEFAULT_AUDIENCE), DEFAULT_AUDIENCE);
        this.sharedSecret = text(Solon.cfg().get("identity.shared-secret", ""), "");
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
                && sharedSecret.length() >= 16;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public String sharedSecret() {
        return sharedSecret;
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
