package xyz.apleax.ALogin.Identity;

/**
 * ALogin 支持的外部身份来源。
 *
 * @author Apleax
 */
public enum ExternalIdentityProvider {
    JAVA_MOJANG,
    BEDROCK_XUID;

    public static ExternalIdentityProvider parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("identity provider is blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported identity provider: " + value, exception);
        }
    }
}
