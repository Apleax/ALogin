package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import static xyz.apleax.ALogin.Util.VerifyCodeUtil.checkVerifyCode;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("account.resetpassword")
public class ResetPasswordServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PasswordEncryptor encryptor;

    public ResetPasswordServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
            @Inject("Algorithm") PasswordEncryptor encryptor) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.encryptor = encryptor;
    }

    @Transaction
    public Result<Boolean> resetPassword(String email, String verify_code, String new_password) {
        if (checkVerifyCode(new VerifyCodeKey(email, VerifyCodeType.RESET_PASSWORD), verify_code))
            return Result.failure("验证码错误");
        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(new_password, salt);
        boolean updated = accountService.update((new LambdaUpdateWrapper<AccountPO>()
                .set(AccountPO::getPassword, password)
                .set(AccountPO::getSalt, salt)
                .eq(AccountPO::getEmail, email)));
        if (updated) {
            String account = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, email));
            if (account != null) accountCache.invalidate(account);
            StpUtil.logout(account);
        } else log.warn("Failed to update password for email: {}", email);
        return Result.succeed(true);
    }
}
