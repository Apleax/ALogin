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
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;

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

    public LoginServiceImpl(
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
    public Result<SaTokenInfo> login(LoginBO loginBO, String token) {
        AccountType accountType = loginBO.getAccount_type();
        String account = getAccountId(loginBO, accountType);
        AccountPO accountPO = null;
        if (account != null) accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure(accountType.getValue() + "或密码错误");
        if (!StpUtil.isLogin()) {
            String password = encryptor.encrypt(loginBO.getPassword(), accountPO.getSalt());
            if (!accountPO.getPassword().equals(password)) return Result.failure(accountType.getValue() + "或密码错误");
            StpUtil.login(accountPO.getAccount(), accountType.getKey());
            boolean updated = accountService.update((new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, loginBO.getReal_ip())
                    .eq(AccountPO::getAccount, accountPO.getAccount())));
            if (updated) accountCache.put(accountPO.getAccount(), accountPO);
            else log.warn("Failed to update last login time for account: {}", accountPO.getAccount());
        }
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        if (token != null) Dami.bus().send("LoginEvent", Map.of("account", account, "token", token));
        return Result.succeed(tokenInfo);
    }

    private String getAccountId(LoginBO loginPO, AccountType accountType) {
        return switch (accountType) {
            case EMAIL -> accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, loginPO.getEmail()));
            case ACCOUNT -> accountIndexCache.get(new AccountIndexCache(AccountType.ACCOUNT, loginPO.getAccount()));
            case QQ_ACCOUNT ->
                    accountIndexCache.get(new AccountIndexCache(AccountType.QQ_ACCOUNT, loginPO.getQq_account()));
            case UUID -> null;
        };
    }
}
