package xyz.apleax.ALogin.Identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * ALogin 与入口适配器之间的短期 HMAC 断言编码器。
 *
 * <p>格式为十个以点分隔的字段：版本、签发者、受众、提供方、主体、名称、签发时间、过期时间、jti、签名。
 * 文本字段使用无填充 Base64URL 编码，签名覆盖前九个字段。</p>
 *
 * @author Apleax
 */
public final class IdentityAssertionCodec {
    private static final String VERSION = "v1";
    private static final int FIELD_COUNT = 10;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private IdentityAssertionCodec() {
    }

    public static String issue(
            VerifiedIdentity identity,
            String issuer,
            String audience,
            String sharedSecret,
            Instant issuedAt,
            Duration lifetime,
            String jti
    ) {
        requireSecret(sharedSecret);
        if (identity == null) throw new IllegalArgumentException("identity is null");
        requireText(issuer, "issuer");
        requireText(audience, "audience");
        if (issuedAt == null) throw new IllegalArgumentException("issuedAt is null");
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("assertion lifetime must be positive");
        }
        if (lifetime.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("assertion lifetime is too long");
        }
        if (jti == null || jti.isBlank()) jti = UUID.randomUUID().toString();
        requireText(jti, "jti");

        Instant expiresAt = issuedAt.plus(lifetime);
        String payload = String.join(".",
                VERSION,
                encode(issuer),
                encode(audience),
                identity.provider().name(),
                encode(identity.subject()),
                encodeOptional(identity.displayName()),
                Long.toString(issuedAt.getEpochSecond()),
                Long.toString(expiresAt.getEpochSecond()),
                encode(jti));
        return payload + "." + encodeBytes(sign(payload, sharedSecret));
    }

    public static IdentityAssertion verify(
            String token,
            String sharedSecret,
            String expectedIssuer,
            String expectedAudience,
            Instant now,
            Duration clockSkew,
            Duration maxLifetime
    ) {
        requireSecret(sharedSecret);
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("invalid identity assertion");
        }
        if (now == null) now = Instant.now();
        if (clockSkew == null || clockSkew.isNegative()) clockSkew = Duration.ZERO;
        if (maxLifetime == null || maxLifetime.isZero() || maxLifetime.isNegative()) {
            throw new IllegalArgumentException("invalid maximum assertion lifetime");
        }

        String[] fields = token.split("\\.", -1);
        if (fields.length != FIELD_COUNT || !VERSION.equals(fields[0])) {
            throw new IllegalArgumentException("invalid identity assertion format");
        }

        byte[] expectedSignature = sign(String.join(".", java.util.Arrays.copyOf(fields, FIELD_COUNT - 1)), sharedSecret);
        byte[] actualSignature = decodeBytes(fields[FIELD_COUNT - 1], "signature");
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new IllegalArgumentException("invalid identity assertion signature");
        }

        String issuer = decode(fields[1], "issuer");
        String audience = decode(fields[2], "audience");
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !Objects.equals(expectedIssuer, issuer)) {
            throw new IllegalArgumentException("unexpected identity assertion issuer");
        }
        if (expectedAudience != null && !expectedAudience.isBlank() && !Objects.equals(expectedAudience, audience)) {
            throw new IllegalArgumentException("unexpected identity assertion audience");
        }

        ExternalIdentityProvider provider = ExternalIdentityProvider.parse(fields[3]);
        String subject = decode(fields[4], "subject");
        String displayName = decodeOptional(fields[5], "displayName");
        long issuedSeconds = parseEpoch(fields[6], "issuedAt");
        long expiresSeconds = parseEpoch(fields[7], "expiresAt");
        String jti = decode(fields[8], "jti");
        Instant issuedAt = Instant.ofEpochSecond(issuedSeconds);
        Instant expiresAt = Instant.ofEpochSecond(expiresSeconds);
        if (!expiresAt.isAfter(issuedAt) || Duration.between(issuedAt, expiresAt).compareTo(maxLifetime) > 0) {
            throw new IllegalArgumentException("invalid identity assertion lifetime");
        }
        if (issuedAt.isAfter(now.plus(clockSkew))) {
            throw new IllegalArgumentException("identity assertion is from the future");
        }
        if (expiresAt.plus(clockSkew).isBefore(now)) {
            throw new IllegalArgumentException("identity assertion is expired");
        }

        return new IdentityAssertion(
                new VerifiedIdentity(provider, subject, displayName, issuer),
                audience,
                issuedAt,
                expiresAt,
                jti);
    }

    private static byte[] sign(String payload, String sharedSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to sign identity assertion", exception);
        }
    }

    private static String encode(String value) {
        requireText(value, "text");
        return encodeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeOptional(String value) {
        if (value == null || value.isBlank()) return "-";
        return encode(value);
    }

    private static String encodeBytes(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static String decode(String value, String field) {
        byte[] bytes = decodeBytes(value, field);
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        requireText(decoded, field);
        return decoded;
    }

    private static String decodeOptional(String value, String field) {
        if ("-".equals(value)) return "";
        return decode(value, field);
    }

    private static byte[] decodeBytes(String value, String field) {
        try {
            byte[] bytes = DECODER.decode(value);
            if (bytes.length > MAX_TEXT_LENGTH) throw new IllegalArgumentException(field + " is too long");
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid identity assertion " + field, exception);
        }
    }

    private static long parseEpoch(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid identity assertion " + field, exception);
        }
    }

    private static void requireSecret(String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank() || sharedSecret.length() < 16) {
            throw new IllegalArgumentException("identity shared secret must contain at least 16 characters");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
    }
}
