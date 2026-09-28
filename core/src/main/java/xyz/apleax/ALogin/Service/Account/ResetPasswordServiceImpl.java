package xyz.apleax.ALogin.Service.Account;

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
import xyz.apleax.ALogin.Util.MailUtil;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import static xyz.apleax.ALogin.Util.VerifyCodeUtil.checkVerifyCode;

/**
 * 重置密码服务
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("account")
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

    /**
     * 重置密码：验证码通过后更新密码/盐/算法，强制登出所有会话，并发送通知邮件。
     *
     * @param email        邮箱
     * @param verify_code  验证码
     * @param new_password 新密码
     */
    @Transaction
    public Result<Boolean> resetPassword(String email, String verify_code, String new_password) {
        if (email == null || email.isBlank()) return Result.failure("邮箱不能为空");
        if (new_password == null || new_password.isEmpty()) return Result.failure("新密码不能为空");

        if (checkVerifyCode(new VerifyCodeKey(email, VerifyCodeType.RESET_PASSWORD), verify_code))
            return Result.failure("验证码错误");

        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(new_password, salt);
        String algorithm = encryptor.algorithmName();

        boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                .set(AccountPO::getPassword, password)
                .set(AccountPO::getSalt, salt)
                .set(AccountPO::getAlgorithm, algorithm)
                .eq(AccountPO::getEmail, email));
        if (!updated) {
            log.warn("重置密码失败：邮箱未注册，email={}", email);
            return Result.failure("该邮箱未注册");
        }
        log.info("重置密码成功：email={}, algorithm={}", email, algorithm);

        String account = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, email));
        if (account == null) {
            log.error("邮箱 [{}] 密码已重置，但账号索引查询失败，无法强制登出/发送通知", email);
            return Result.succeed(true);
        }

        accountCache.invalidate(account);

        try {
            StpUtil.logout(account);
        } catch (Exception e) {
            log.warn("重置密码后登出账号 [{}] 失败: {}", account, e.getMessage());
        }
        
        MailUtil.sendPasswordResetNotifyAsync(email, account);

        return Result.succeed(true);
    }
}
