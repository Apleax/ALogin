package xyz.apleax.ALogin.Service.Impl;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.ConvertMapper.AccountAPIConvert;
import xyz.apleax.ALogin.Entity.DTO.LoginByEmailDTO;
import xyz.apleax.ALogin.Entity.DTO.LoginByMcUuidDTO;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.PO.AccountPO;
import xyz.apleax.ALogin.Entity.PO.LoginPO;
import xyz.apleax.ALogin.Entity.POJO.AccountIndexCache;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.Enum.AccountIndex;
import xyz.apleax.ALogin.Enum.LoginDeviceType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Service.AccountService;
import xyz.apleax.ALogin.Util.EmailVerifyCodeUtil;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.util.List;

/**
 * @author Apleax
 */
@Slf4j
@Component
public class AccountServiceImpl implements AccountService {
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

    @Override
    public Result<SaTokenInfo> register(RegisterDTO registerDTO, Context context) throws Exception {
        AccountPO accountPO = AccountAPIConvert.INSTANCE.registerDTOToAccountPO(registerDTO);
        if (checkVerifyCode(new VerifyCodeKey(registerDTO.getEmail(), VerifyCodeType.REGISTER), registerDTO.getVerify_code()))
            return Result.failure("验证码错误");
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountIndex.EMAIL, accountPO.getEmail()));
        if (accountId != null) return Result.failure("该邮箱已注册");
        accountPO = createAccount(accountPO);
        if (accountPO == null) return Result.failure("注册失败");
        accountPO.setLastLoginWebTime(System.currentTimeMillis() / 1000);
        if (!accountService.save(accountPO)) {
            log.warn("Failed to save account: {}", context.realIp());
            return Result.failure("注册失败");
        }
        accountIndexCache.put(new AccountIndexCache(AccountIndex.ACCOUNT, accountPO.getAccount()), accountPO.getAccount());
        accountCache.put(accountPO.getAccount(), accountPO);
        StpUtil.login(accountPO.getAccount(), LoginDeviceType.getKeyByValue(LoginDeviceType.BROWSER));
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.succeed(tokenInfo);
    }

    private boolean checkVerifyCode(VerifyCodeKey key, String verifyCode) {
        VerifyCodePOJO code = verifyCodeCache.get(key);
        if (code == null || !code.getVerifyCode().equals(verifyCode)) return true;
        verifyCodeCache.invalidate(key);
        return false;
    }

    private AccountPO createAccount(AccountPO accountPO) throws Exception {
        for (int i = 0; i < 10; i++) {
            String account = RandomStringUtils.generateNum(8);
            if (account.charAt(0) == '0') continue;
            if (accountService.exists(new LambdaQueryWrapper<AccountPO>()
                    .select(AccountPO::getAccount)
                    .eq(AccountPO::getAccount, account))) continue;
            accountPO.setAccount(account);
            break;
        }
        if (accountPO.getAccount() == null) return null;
        accountPO.setRegistrationTime(System.currentTimeMillis() / 1000);
        accountPO.setNickName("用户" + accountPO.getAccount());
        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(accountPO.getPassword(), salt);
        accountPO.setPassword(password);
        accountPO.setSalt(salt);
        accountPO.setAlgorithm(encryptor.algorithmName());
        accountPO.setMcUuid("NULL");
        return accountPO;
    }

    @Override
    public Result<Long> registerVerifyCode(String email) {
        VerifyCodePOJO verifyCodePOJO = verifyCodeCache.get(new VerifyCodeKey(email, VerifyCodeType.REGISTER));
        if (verifyCodePOJO == null) return Result.failure(Result.FAILURE_CODE, "该邮箱已注册");
        if (verifyCodePOJO.getTime() != null) {
            long remainderTime = (System.currentTimeMillis() / 1000 - verifyCodePOJO.getTime());
            if (remainderTime < 60) return Result.failure(Result.FAILURE_CODE, "获取失败", 60 - remainderTime);
        }
        verifyCodePOJO.setTime(System.currentTimeMillis() / 1000);
        String VCode = verifyCodePOJO.getVerifyCode();
        EmailVerifyCodeUtil.sendAsync(email, VCode);
        return Result.succeed();
    }

    @Override
    public Result<SaTokenInfo> loginByEmail(LoginByEmailDTO loginByEmailDTO) throws Exception {
        LoginDeviceType loginDeviceType = LoginDeviceType.getValueByKey(loginByEmailDTO.getLogin_type());
        if (loginDeviceType == null) return Result.failure("登录类型错误");
        LoginPO loginPO = AccountAPIConvert.INSTANCE.loginByEmailDTOToLoginPO(loginByEmailDTO);
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountIndex.EMAIL, loginPO.getEmail()));
        AccountPO accountPO = null;
        if (accountId != null) accountPO = accountCache.get(accountId);
        if (accountPO == null) return Result.failure("邮箱或密码错误");
        String password = encryptor.encrypt(loginPO.getPassword(), accountPO.getSalt());
        if (!accountPO.getPassword().equals(password)) return Result.failure("邮箱或密码错误");
        StpUtil.login(accountPO.getAccount(), loginDeviceType.getKey());
        if (loginDeviceType == LoginDeviceType.BROWSER) {
            accountPO.setLastLoginWebTime(System.currentTimeMillis() / 1000);
            boolean updated = accountService.update((new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginWebTime, accountPO.getLastLoginWebTime())
                    .eq(AccountPO::getAccount, accountPO.getAccount())));
            if (updated) accountCache.put(accountPO.getAccount(), accountPO);
            else log.warn("Failed to update last login time for account: {}", accountPO.getAccount());
        }
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.succeed(tokenInfo);
    }

    @Override
    public Result<SaTokenInfo> loginByMcUuid(LoginByMcUuidDTO loginByMcUuidDTO) throws Exception {
        LoginPO loginPO = AccountAPIConvert.INSTANCE.loginByMcUuidDTOToLoginPO(loginByMcUuidDTO);
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountIndex.MC_UUID, loginPO.getMc_uuid()));
        AccountPO accountPO = null;
        if (accountId != null) accountPO = accountCache.get(accountId);
        if (accountPO == null) return Result.failure(400101, "MC_UUID未绑定账号");
        List<SaTerminalInfo> saTerminalInfos = StpUtil.getTerminalListByLoginId(accountId);
        if (saTerminalInfos.isEmpty()) {
            if (loginPO.getPassword() != null) {
                String password = encryptor.encrypt(loginPO.getPassword(), accountPO.getSalt());
                if (!accountPO.getPassword().equals(password)) return Result.failure("密码错误");
                StpUtil.login(accountPO.getAccount(), LoginDeviceType.MINECRAFT.getKey());
            }
        } else {
            for (SaTerminalInfo saTerminalInfo : saTerminalInfos)
                if (saTerminalInfo.getDeviceType().equals(LoginDeviceType.MINECRAFT.getKey()))
                    return Result.succeed(StpUtil.getTokenInfo());
            StpUtil.login(accountPO.getAccount(), LoginDeviceType.MINECRAFT.getKey());
        }
        return Result.succeed(StpUtil.getTokenInfo());
    }
}
