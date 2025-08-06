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
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.PO.AccountPO;
import xyz.apleax.ALogin.Entity.POJO.AccountIndexCache;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.Entity.VO.RegisterVO;
import xyz.apleax.ALogin.Enum.AccountIndex;
import xyz.apleax.ALogin.Enum.LoginDeviceType;
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
    private LoadingCache<String, VerifyCodePOJO> verifyCodeCache;
    @Inject("AccountCache")
    private LoadingCache<String, AccountPO> accountCache;
    @Inject("AccountIndexCache")
    private LoadingCache<AccountIndexCache, String> accountIndexCache;
    @Inject("Algorithm")
    private PasswordEncryptor encryptor;

    @Override
    public Result<RegisterVO> register(RegisterDTO registerDTO) throws Exception {
        AccountPO accountPO = AccountAPIConvert.INSTANCE.registerDTOToAccountPO(registerDTO);
        VerifyCodePOJO verifyCode = verifyCodeCache.get(accountPO.getEmail());
        if (verifyCode == null || !verifyCode.getVerifyCode().equals(registerDTO.getVerify_code()))
            return Result.failure("验证码错误");
        verifyCodeCache.invalidate(accountPO.getEmail());
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountIndex.EMAIL, accountPO.getEmail()));
        if (accountId != null) return Result.failure("该邮箱已注册");
        for (int i = 0; i < 10; i++) {
            String account = RandomStringUtils.generateNum(8);
            if (account.charAt(0) == '0') continue;
            if (accountService.exists(new LambdaQueryWrapper<AccountPO>()
                    .select(AccountPO::getAccount)
                    .eq(AccountPO::getAccount, account))) continue;
            accountPO.setAccount(account);
            break;
        }
        if (accountPO.getAccount() == null) return Result.failure("帐号生成失败，请尝试重试");
        accountPO.setRegistrationTime(System.currentTimeMillis() / 1000);
        accountPO.setNickName("用户" + accountPO.getAccount());
        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(registerDTO.getPassword(), salt);
        accountPO.setPassword(password);
        accountPO.setSalt(salt);
        accountPO.setAlgorithm(encryptor.algorithmName());
        if (!accountService.save(accountPO)) return Result.failure("注册失败");
        accountIndexCache.put(new AccountIndexCache(AccountIndex.ACCOUNT, accountPO.getAccount()), accountPO.getAccount());
        accountCache.put(accountPO.getAccount(), accountPO);
        StpUtil.login(accountPO.getAccount(), LoginDeviceType.getKeyByValue(LoginDeviceType.BROWSER));
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.succeed(new RegisterVO(accountPO.getAccount(), tokenInfo));
    }

    @Override
    public Result<Long> emailVerifyCode(String email) {
        VerifyCodePOJO verifyCodePOJO = verifyCodeCache.get(email);
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
}
