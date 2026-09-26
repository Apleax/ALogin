package xyz.apleax.ALogin.Identity;

/**
 * 已由上游认证组件确认的外部身份。
 *
 * @param provider 身份来源
 * @param subject  来源内稳定且不可由玩家自报的主体标识
 * @param displayName 展示用名称快照，可为空
 * @param issuer 负责验证的上游组件
 * @author Apleax
 */
public record VerifiedIdentity(
        ExternalIdentityProvider provider,
        String subject,
        String displayName,
        String issuer
) {
    public VerifiedIdentity {
        if (provider == null) throw new IllegalArgumentException("identity provider is null");
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("identity subject is blank");
        subject = subject.trim();
        displayName = displayName == null ? "" : displayName.trim();
        issuer = issuer == null ? "" : issuer.trim();
    }
}
