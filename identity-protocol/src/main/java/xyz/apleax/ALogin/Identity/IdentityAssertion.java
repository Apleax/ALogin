package xyz.apleax.ALogin.Identity;

import java.time.Instant;

/**
 * 通过共享密钥验证后的短期外部身份断言。
 *
 * @author Apleax
 */
public record IdentityAssertion(
        VerifiedIdentity identity,
        String audience,
        Instant issuedAt,
        Instant expiresAt,
        String jti
) {
    public IdentityAssertion {
        if (identity == null) throw new IllegalArgumentException("identity is null");
        if (audience == null || audience.isBlank()) throw new IllegalArgumentException("audience is blank");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("invalid assertion lifetime");
        }
        if (jti == null || jti.isBlank()) throw new IllegalArgumentException("jti is blank");
    }
}
