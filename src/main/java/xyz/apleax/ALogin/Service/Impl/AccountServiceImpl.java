package xyz.apleax.ALogin.Service.Impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.ConvertMapper.AccountAPIConvert;
import xyz.apleax.ALogin.Entity.DTO.LoginByEmailDTO;
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

/**
 * @author Apleax
 */
@Slf4j
@Component
public class AccountServiceImpl implements AccountService {
    @Ds("DataBase")
    private IAccountService accountService;
    @Inject("VerifyCode")
    private LoadingCache<VerifyCodeKey, VerifyCodePOJO> verifyCodeCache;
    @Inject("AccountCache")
    private LoadingCache<String, AccountPO> accountCache;
    @Inject("AccountIndexCache")
    private LoadingCache<AccountIndexCache, String> accountIndexCache;
    @Inject("Algorithm")
    private PasswordEncryptor encryptor;

    @Override
    public Result<SaTokenInfo> register(RegisterDTO registerDTO) throws Exception {
        AccountPO accountPO = AccountAPIConvert.INSTANCE.registerDTOToAccountPO(registerDTO);
        if (checkVerifyCode(new VerifyCodeKey(registerDTO.getEmail(), VerifyCodeType.REGISTER), registerDTO.getVerify_code()))
            return Result.failure("验证码错误");
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountIndex.EMAIL, accountPO.getEmail()));
        if (accountId != null) return Result.failure("该邮箱已注册");
        accountPO = createAccount(accountPO);
        if (accountPO == null) return Result.failure("注册失败");
        if (!accountService.save(accountPO)) return Result.failure("注册失败");
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
        String password = encryptor.encrypt(loginByEmailDTO.getPassword(), accountPO.getSalt());
        if (!accountPO.getPassword().equals(password)) return Result.failure("邮箱或密码错误");
        StpUtil.login(accountPO.getAccount(), loginDeviceType.getKey());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.succeed(tokenInfo);
    }
}
