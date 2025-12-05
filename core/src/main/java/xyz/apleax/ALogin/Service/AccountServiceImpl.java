package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.ConvertMapper.BOtoPOConvert;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.EmailVerifyCodeUtil;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("account")
public class AccountServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PasswordEncryptor encryptor;

    public AccountServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("VerifyCode") LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
            @Inject("Algorithm") PasswordEncryptor encryptor) {
        this.accountService = accountService;
        this.verifyCodeCache = verifyCodeCache;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.encryptor = encryptor;
    }

    @Transaction
    public Result<SaTokenInfo> register(AccountBO accountBO, String verify_code, String real_ip, String token) throws Exception {
        if (checkVerifyCode(new VerifyCodeKey(accountBO.getEmail(), VerifyCodeType.REGISTER), verify_code))
            return Result.failure("验证码错误");
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, accountBO.getEmail()));
        if (accountId != null) return Result.failure("该邮箱已注册");
        accountBO = createAccount(accountBO);
        if (accountBO == null) return Result.failure("注册失败");
        AccountPO accountPO = BOtoPOConvert.INSTANCE.registerBOToAccountPO(accountBO);
        if (!accountService.save(accountPO)) {
            log.warn("Failed to save account: {}", real_ip);
            return Result.failure("注册失败");
        }
        accountIndexCache.put(new AccountIndexCache(AccountType.ACCOUNT, accountPO.getAccount()), accountPO.getAccount());
        accountCache.put(accountPO.getAccount(), accountPO);
        StpUtil.login(accountPO.getAccount(), AccountType.EMAIL.getKey());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        if (token != null) Dami.bus().send("LoginEvent", Map.of("account", accountPO, "token", token));
        return Result.succeed(tokenInfo);
    }

    private boolean checkVerifyCode(VerifyCodeKey key, String verifyCode) {
        VerifyCodePOJO code = verifyCodeCache.get(key);
        if (code == null || !code.getVerifyCode().equals(verifyCode)) return true;
        verifyCodeCache.invalidate(key);
        return false;
    }

    private AccountBO createAccount(AccountBO accountBO) throws Exception {
        for (int i = 0; i < 12; i++) {
            String account = RandomStringUtils.generateNum(8);
            if (account.charAt(0) == '0') continue;
            if (accountService.exists(new LambdaQueryWrapper<AccountPO>()
                    .select(AccountPO::getAccount)
                    .eq(AccountPO::getAccount, account))) continue;
            accountBO.setAccount(account);
            break;
        }
        if (accountBO.getAccount() == null) return null;
        accountBO.setRegistrationTime(System.currentTimeMillis() / 1000);
        accountBO.setNickName("用户" + accountBO.getAccount());
        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(accountBO.getPassword(), salt);
        accountBO.setPassword(password);
        accountBO.setSalt(salt);
        accountBO.setAlgorithm(encryptor.algorithmName());
        accountBO.setMcUuid(UUID.nameUUIDFromBytes(("Account:" + accountBO.getAccount())
                .getBytes(StandardCharsets.UTF_8)));
        return accountBO;
    }

    @Transaction
    public Result<SaTokenInfo> login(LoginBO loginBO, String token) throws Exception {
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
        };
    }

    @Transaction
    public Result<Long> verifyCode(VerifyCodeKey verifyCodeKey) {
        if (verifyCodeKey.type() == VerifyCodeType.RESET_PASSWORD) {
            LoginBO loginBO = new LoginBO();
            loginBO.setEmail(verifyCodeKey.email());
            String accountId = getAccountId(loginBO, AccountType.EMAIL);
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
        EmailVerifyCodeUtil.sendAsync(verifyCodeKey.email(), VCode);
        return Result.succeed();
    }

    @Transaction
    public Result<SaTokenInfo> getLoginInfo() {
        return Result.succeed(StpUtil.getTokenInfo());
    }

    @Transaction
    public Result<SaTokenInfo> logout() {
        StpUtil.logout();
        return Result.succeed();
    }

    @Transaction
    public Result<Boolean> resetPassword(String email, String verify_code, String new_password) throws Exception {
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

    @Transaction
    public GameProfile checkToken(String token) {
        String account;
        if (token == null) account = StpUtil.getLoginIdAsString();
        else account = (String) StpUtil.getLoginIdByToken(token);
        if (account == null) return null;
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return null;
        return new GameProfile(accountPO.getMcUuid(), accountPO.getNickName(), Collections.emptyList());
    }
}
