package xyz.apleax.ALogin.Service.Account;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.Dami;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.Encrypt.EncryptorSelector;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.MailUtil;

import java.util.Map;

/**
 * 登录服务
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("account")
public class LoginServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    private final PasswordEncryptor encryptor;

    private final EncryptorSelector selector;

    public LoginServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
            @Inject("Algorithm") PasswordEncryptor encryptor,
            EncryptorSelector selector) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.encryptor = encryptor;
        this.selector = selector;
    }

    @Transaction
    public Result<SaTokenInfo> login(LoginBO loginBO, String token) {
        if (loginBO == null) return Result.failure("请求参数缺失");
        AccountType accountType = loginBO.getAccount_type();
        if (accountType == null) return Result.failure("登录类型缺失");
        if (accountType == AccountType.UUID) {
            log.debug("拒绝 UUID 登录尝试，realIp={}", loginBO.getReal_ip());
            return Result.failure("暂不支持该登录方式");
        }
        String rawPassword = loginBO.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) return Result.failure("密码不能为空");
        String realIp = loginBO.getReal_ip();
        if (realIp == null || realIp.isBlank()) {
            log.warn("登录请求缺失 real_ip，已拒绝");
            return Result.failure("无法获取客户端 IP，请稍后再试");
        }

        String account = getAccountId(loginBO, accountType);
        if (account == null) {
            log.debug("登录失败：账号不存在，type={}, realIp={}", accountType, realIp);
            return Result.failure(accountType.getValue() + "或密码错误");
        }
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure(accountType.getValue() + "或密码错误");

        boolean alertAlreadySent = false;
        if (StpUtil.isLogin()) {
            String storedIp = accountPO.getLastLoginIp();
            if (storedIp != null && storedIp.equals(realIp)) {
                StpUtil.updateLastActiveToNow();
                SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
                dispatchLoginEvent(account, token);
                log.info("登录成功，email={}, mcUuid={}, loginType=自动登录", accountPO.getEmail(), accountPO.getMcUuid());
                return Result.succeed(tokenInfo);
            }
            log.warn("检测到异地登录，废弃当前 token，account={}, oldIp={}, newIp={}",
                    account, storedIp, realIp);
            StpUtil.logout();
            if (storedIp != null) {
                MailUtil.sendIpChangeAlertAsync(accountPO.getEmail(), account, storedIp, realIp);
                alertAlreadySent = true;
            }
        }

        String storedPassword = accountPO.getPassword();
        String storedSalt = accountPO.getSalt();
        if (storedPassword == null || storedSalt == null) {
            log.warn("账号 [{}] 密码或盐值缺失，无法登录", account);
            return Result.failure(accountType.getValue() + "或密码错误");
        }

        String storedAlgorithm = accountPO.getAlgorithm();
        PasswordEncryptor actualEncryptor;
        if (storedAlgorithm == null || storedAlgorithm.isBlank()) {
            log.debug("账号 [{}] 未记录加密算法，使用默认算法 {}", account, encryptor.algorithmName());
            actualEncryptor = encryptor;
        } else {
            actualEncryptor = selector.select(storedAlgorithm);
            if (actualEncryptor == null) {
                log.error("账号 [{}] 使用的加密算法 [{}] 已不可用，无法登录", account, storedAlgorithm);
                return Result.failure("该账号使用的加密算法已失效，请联系管理员");
            }
        }
        String hashed = actualEncryptor.encrypt(rawPassword, storedSalt);
        if (!storedPassword.equals(hashed)) {
            log.debug("登录失败：密码错误，account={}", account);
            return Result.failure(accountType.getValue() + "或密码错误");
        }

        String oldIp = accountPO.getLastLoginIp();
        boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                .set(AccountPO::getLastLoginIp, realIp)
                .eq(AccountPO::getAccount, account));
        if (!updated) {
            log.error("更新最后登录 IP 失败，拒绝登录，account={}", account);
            return Result.failure("登录失败，请稍后再试");
        }

        accountCache.invalidate(account);

        StpUtil.login(account, accountType.getKey());
        log.info("登录成功，email={}, mcUuid={}", accountPO.getEmail(), accountPO.getMcUuid());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        if (!alertAlreadySent && oldIp != null && !oldIp.equals(realIp))
            MailUtil.sendIpChangeAlertAsync(accountPO.getEmail(), account, oldIp, realIp);
        
        dispatchLoginEvent(account, token);
        return Result.succeed(tokenInfo);
    }

    private void dispatchLoginEvent(String account, String token) {
        if (token == null) return;
        Dami.bus().send("LoginEvent", Map.of("account", account, "token", token));
    }

    private String getAccountId(LoginBO loginBO, AccountType accountType) {
        String identifier = switch (accountType) {
            case EMAIL -> loginBO.getEmail();
            case ACCOUNT -> loginBO.getAccount();
            case QQ_ACCOUNT -> loginBO.getQq_account();
            case UUID -> null;
        };
        if (identifier == null || identifier.isBlank()) return null;
        return accountIndexCache.get(new AccountIndexCache(accountType, identifier));
    }
}
