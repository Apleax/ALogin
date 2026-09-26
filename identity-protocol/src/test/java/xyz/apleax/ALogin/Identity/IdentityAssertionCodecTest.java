package xyz.apleax.ALogin.Identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityAssertionCodecTest {
    private static final String SECRET = "test-shared-secret-20260926";
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void issuesAndVerifiesAnAssertion() {
        VerifiedIdentity identity = new VerifiedIdentity(
                ExternalIdentityProvider.BEDROCK_XUID,
                "2535400000000000",
                "Player",
                "geyser-hk");
        String token = IdentityAssertionCodec.issue(
                identity, "geyser-hk", "ALogin", SECRET, NOW, Duration.ofSeconds(30), "jti-1");

        IdentityAssertion assertion = IdentityAssertionCodec.verify(
                token, SECRET, "geyser-hk", "ALogin", NOW, Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertEquals(identity, assertion.identity());
        assertEquals("jti-1", assertion.jti());
        assertEquals(NOW.plusSeconds(30), assertion.expiresAt());
    }

    @Test
    void rejectsTamperedAssertions() {
        String token = IdentityAssertionCodec.issue(
                new VerifiedIdentity(ExternalIdentityProvider.JAVA_MOJANG,
                        "550e8400-e29b-41d4-a716-446655440000", "Player", "velocity"),
                "velocity", "ALogin", SECRET, NOW, Duration.ofSeconds(30), "jti-2");

        int signatureStart = token.lastIndexOf('.') + 1;
        char signatureCharacter = token.charAt(signatureStart);
        char replacement = signatureCharacter == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        assertThrows(IllegalArgumentException.class, () -> IdentityAssertionCodec.verify(
                tampered, SECRET, "velocity", "ALogin", NOW, Duration.ofSeconds(1), Duration.ofMinutes(1)));
    }

    @Test
    void rejectsExpiredAssertions() {
        String token = IdentityAssertionCodec.issue(
                new VerifiedIdentity(ExternalIdentityProvider.BEDROCK_XUID,
                        "2535400000000000", "Player", "geyser"),
                "geyser", "ALogin", SECRET, NOW, Duration.ofSeconds(10), "jti-3");

        assertThrows(IllegalArgumentException.class, () -> IdentityAssertionCodec.verify(
                token, SECRET, "geyser", "ALogin", NOW.plusSeconds(12), Duration.ZERO, Duration.ofMinutes(1)));
    }
}
