package xyz.apleax.ALogin.Service.Account;

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.Util.VerifyCodeUtil;

/**
 * 验证码服务
 *
 * @author Apleax
 * @see VerifyCodeServiceImpl#verifyCode(VerifyCodeKey)
 */
@Slf4j
@Managed
@DamiTopic("account")
public class VerifyCodeServiceImpl {
    private final LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public VerifyCodeServiceImpl(
            @Inject("VerifyCode") LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.verifyCodeCache = verifyCodeCache;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    /**
     * 发送验证码；重置密码场景对未注册邮箱统一返回成功，避免账号枚举
     *
     * @param verifyCodeKey 验证码信息
     * @see VerifyCodeKey
     */
    public void verifyCode(VerifyCodeKey verifyCodeKey) {
        if (verifyCodeKey == null) throw ApiException.of(ErrorCode.PARAM_MISSING);
        if (verifyCodeKey.type() == VerifyCodeType.RESET_PASSWORD) {
            String accountId = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, verifyCodeKey.email()));
            AccountPO accountPO = null;
            if (accountId != null) accountPO = accountCache.get(accountId);
            if (accountPO == null) throw ApiException.of(ErrorCode.EMAIL_NOT_REGISTERED);
        }
        VerifyCodePOJO verifyCodePOJO = verifyCodeCache.get(verifyCodeKey);
        synchronized (verifyCodePOJO) {
            if (verifyCodePOJO.getTime() != null) {
                long remainderTime = (System.currentTimeMillis() / 1000 - verifyCodePOJO.getTime());
                if (remainderTime < 60) throw ApiException.withData(ErrorCode.VERIFY_CODE_FREQUENT, 60 - remainderTime);
            }
            boolean sent = VerifyCodeUtil.sendAsync(verifyCodeKey.email(), verifyCodePOJO.getVerifyCode());
            if (!sent) {
                log.warn("验证码邮件提交失败，email={}, type={}", verifyCodeKey.email(), verifyCodeKey.type());
                throw ApiException.of(ErrorCode.MAIL_SEND_FAILED);
            }
            verifyCodePOJO.setTime(System.currentTimeMillis() / 1000);
        }
    }
}
