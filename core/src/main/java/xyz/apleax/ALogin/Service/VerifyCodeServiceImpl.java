package xyz.apleax.ALogin.Service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
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
@DamiTopic("account.verifycode")
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
     * 发送验证码
     *
     * @param verifyCodeKey 验证码信息
     * @return 验证码发送结果
     * @see VerifyCodeKey
     */
    @Transaction
    public Result<Long> verifyCode(VerifyCodeKey verifyCodeKey) {
        if (verifyCodeKey.type() == VerifyCodeType.RESET_PASSWORD) {
            LoginBO loginBO = new LoginBO();
            loginBO.setEmail(verifyCodeKey.email());
            String accountId = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, loginBO.getEmail()));
            AccountPO accountPO = null;
            if (accountId != null) accountPO = accountCache.get(accountId);
            if (accountPO == null) return Result.failure("账号不存在");
        }
        VerifyCodePOJO verifyCodePOJO = verifyCodeCache.get(verifyCodeKey);
        if (verifyCodePOJO == null) return Result.failure();
        if (verifyCodePOJO.getTime() != null) {
            long remainderTime = (System.currentTimeMillis() / 1000 - verifyCodePOJO.getTime());
            if (remainderTime < 60) return Result.failure(Result.FAILURE_CODE, "获取失败", 60 - remainderTime);
        }
        verifyCodePOJO.setTime(System.currentTimeMillis() / 1000);
        String VCode = verifyCodePOJO.getVerifyCode();
        VerifyCodeUtil.sendAsync(verifyCodeKey.email(), VCode);
        return Result.succeed();
    }
}
