package xyz.apleax.ALogin.Identity;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.Identity.ExternalIdentityProvider;
import xyz.apleax.ALogin.Identity.IdentityAssertion;
import xyz.apleax.ALogin.Identity.IdentityAssertionCodec;
import xyz.apleax.ALogin.Identity.VerifiedIdentity;
import xyz.apleax.ALogin.PO.AccountIdentityPO;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.SQL.Service.IAccountIdentityService;
import xyz.apleax.ALogin.SQL.Service.IIdentityAssertionReplayService;
import xyz.apleax.ALogin.SQL.Service.IAccountService;

import java.time.Instant;
import java.util.Optional;

/**
 * 负责验证外部身份断言、查询绑定关系、创建 ALogin 会话和绑定新身份。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class IdentityLoginService {
    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final IAccountIdentityService identityService;
    private final IAccountService accountService;
    private final IdentityConfig config;
    private final IIdentityAssertionReplayService replayService;
    private final Cache<String, Boolean> replayCleanup = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(java.time.Duration.ofMinutes(5))
            .build();

    public IdentityLoginService(
            @Ds("DataBase") IAccountIdentityService identityService,
            @Ds("DataBase") IAccountService accountService,
            @Ds("DataBase") IIdentityAssertionReplayService replayService,
            IdentityConfig config) {
        this.identityService = identityService;
        this.accountService = accountService;
        this.config = config;
        this.replayService = replayService;
    }

    /**
     * 验证断言并解析绑定关系。调用成功后 jti 已被消费，不能再次使用同一断言。
     */
    public IdentityResolution resolve(String token) {
        if (!config.enabled()) throw new IdentityBindingException(
                "identity.error.disabled", "外部身份登录未启用或共享密钥未配置");
        IdentityAssertion assertion;
        try {
            ExternalIdentityProvider provider = IdentityAssertionCodec.providerFromUnverified(token);
            String secret = config.secretFor(provider);
            if (secret == null) throw new IllegalArgumentException("identity provider is disabled");
            assertion = IdentityAssertionCodec.verify(
                    token,
                    secret,
                    config.issuer(),
                    config.audience(),
                    provider,
                    Instant.now(),
                    config.clockSkew(),
                    config.assertionMaxLifetime());
        } catch (IllegalArgumentException exception) {
            throw new IdentityBindingException(
                    "identity.error.assertion-invalid", "外部身份断言无效", exception);
        }
        try {
            cleanupReplayRecords();
            if (!replayService.consumeOnce(assertion.jti(), assertion.expiresAt().toEpochMilli())) {
                throw new IdentityBindingException("identity.error.assertion-replayed", "外部身份断言已使用");
            }
        } catch (IdentityBindingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdentityBindingException(
                    "identity.error.replay-store-unavailable", "外部身份断言防重放存储不可用", exception);
        }

        AccountIdentityPO binding = identityService.getOne(new LambdaQueryWrapper<AccountIdentityPO>()
                .eq(AccountIdentityPO::getProvider, assertion.identity().provider().name())
                .eq(AccountIdentityPO::getSubject, normalizeSubject(assertion.identity())));
        if (binding == null) return new IdentityResolution(assertion, null, null);
        AccountPO account = accountService.getById(binding.getAccountId());
        if (account == null) throw new IdentityBindingException(
                "identity.error.account-missing", "外部身份绑定的账号不存在");
        return new IdentityResolution(assertion, binding, account);
    }

    /**
     * 用已解析的绑定创建现有 ALogin Sa-Token 会话。
     */
    @Transaction
    public String login(IdentityResolution resolution, String realIp) {
        if (resolution == null || !resolution.isBound()) {
            throw new IdentityBindingException(
                    "identity.error.account-unbound", "外部身份尚未绑定 ALogin 账号");
        }
        AccountPO account = resolution.account();
        SaTokenContextMockUtil.setMockContext();
        StpUtil.login(account.getAccount(), resolution.assertion().identity().provider().name());
        identityService.update(new LambdaUpdateWrapper<AccountIdentityPO>()
                .set(AccountIdentityPO::getLastSeenAt, System.currentTimeMillis())
                .set(AccountIdentityPO::getUpdatedAt, System.currentTimeMillis())
                .eq(AccountIdentityPO::getId, resolution.binding().getId()));
        boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                .set(AccountPO::getLastLoginIp, realIp)
                .eq(AccountPO::getId, account.getId()));
        if (!updated) log.warn("Failed to update last login IP for identity account: {}", account.getAccount());
        return StpUtil.getTokenValueByLoginId(account.getAccount());
    }

    /**
     * 把当前已登录的 ALogin 账号绑定到一次已经验证的外部身份。
     */
    @Transaction
    public void bind(String account, VerifiedIdentity identity) {
        if (account == null || account.isBlank()) throw new IdentityBindingException(
                "identity.error.account-invalid", "ALogin 账号不存在");
        if (identity == null) throw new IdentityBindingException(
                "identity.error.identity-missing", "没有可绑定的已验证外部身份");
        AccountPO accountPO = accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getAccount, account));
        if (accountPO == null) throw new IdentityBindingException(
                "identity.error.account-invalid", "ALogin 账号不存在");

        String subject = normalizeSubject(identity);
        AccountIdentityPO existing = identityService.getOne(new LambdaQueryWrapper<AccountIdentityPO>()
                .eq(AccountIdentityPO::getProvider, identity.provider().name())
                .eq(AccountIdentityPO::getSubject, subject));
        if (existing != null) {
            if (!accountPO.getId().equals(existing.getAccountId())) {
                throw new IdentityBindingException(
                        "identity.error.identity-other-account", "这个外部身份已经绑定到其他 ALogin 账号");
            }
            if (ACTIVE.equalsIgnoreCase(existing.getStatus())) return;
            long now = System.currentTimeMillis();
            boolean restored = identityService.update(new LambdaUpdateWrapper<AccountIdentityPO>()
                    .set(AccountIdentityPO::getDisplayName, identity.displayName())
                    .set(AccountIdentityPO::getIssuer, identity.issuer())
                    .set(AccountIdentityPO::getStatus, ACTIVE)
                    .set(AccountIdentityPO::getVerifiedAt, now)
                    .set(AccountIdentityPO::getLastSeenAt, now)
                    .set(AccountIdentityPO::getUpdatedAt, now)
                    .eq(AccountIdentityPO::getId, existing.getId())
                    .eq(AccountIdentityPO::getAccountId, accountPO.getId()));
            if (!restored) throw new IdentityBindingException(
                    "identity.error.identity-restore-failed", "恢复外部身份绑定失败");
            return;
        }

        AccountIdentityPO sameProvider = identityService.getOne(new LambdaQueryWrapper<AccountIdentityPO>()
                .eq(AccountIdentityPO::getAccountId, accountPO.getId())
                .eq(AccountIdentityPO::getProvider, identity.provider().name())
                .eq(AccountIdentityPO::getStatus, ACTIVE));
        if (sameProvider != null && !subject.equals(sameProvider.getSubject())) {
            throw new IdentityBindingException(
                    "identity.error.provider-already-bound", "这个 ALogin 账号已经绑定了同类外部身份");
        }

        long now = System.currentTimeMillis();
        AccountIdentityPO binding = new AccountIdentityPO(
                null,
                accountPO.getId(),
                identity.provider().name(),
                subject,
                identity.displayName(),
                identity.issuer(),
                ACTIVE,
                now,
                now,
                now,
                now);
        try {
            if (!identityService.save(binding)) throw new IdentityBindingException(
                    "identity.error.identity-save-failed", "保存外部身份绑定失败");
        } catch (RuntimeException exception) {
            if (exception instanceof IdentityBindingException bindingException) throw bindingException;
            throw new IdentityBindingException(
                    "identity.error.identity-save-conflict", "保存外部身份绑定失败，可能已被其他请求占用", exception);
        }
    }

    public Optional<AccountIdentityPO> find(ExternalIdentityProvider provider, String subject) {
        if (provider == null || subject == null || subject.isBlank()) return Optional.empty();
        return Optional.ofNullable(identityService.getOne(new LambdaQueryWrapper<AccountIdentityPO>()
                .eq(AccountIdentityPO::getProvider, provider.name())
                .eq(AccountIdentityPO::getSubject, normalizeSubject(provider, subject))));
    }

    private static String normalizeSubject(VerifiedIdentity identity) {
        return normalizeSubject(identity.provider(), identity.subject());
    }

    private static String normalizeSubject(ExternalIdentityProvider provider, String subject) {
        String normalized = subject.trim();
        return provider == ExternalIdentityProvider.JAVA_MOJANG
                ? normalized.toLowerCase(java.util.Locale.ROOT)
                : normalized;
    }

    private void cleanupReplayRecords() {
        if (replayCleanup.getIfPresent("done") != null) return;
        replayService.removeExpired(System.currentTimeMillis());
        replayCleanup.put("done", Boolean.TRUE);
    }

    public record IdentityResolution(
            IdentityAssertion assertion,
            AccountIdentityPO binding,
            AccountPO account
    ) {
        public boolean isBound() {
            return binding != null && account != null && ACTIVE.equalsIgnoreCase(binding.getStatus());
        }
    }
}
