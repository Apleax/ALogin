package xyz.apleax.ALogin.Service.Account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.ConvertMapper.BOtoPOConvert;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static xyz.apleax.ALogin.Util.VerifyCodeUtil.checkVerifyCode;

/**
 * 注册服务
 *
 * @author Apleax
 * @see RegisterServiceImpl#register(AccountBO, String, String)
 */
@Slf4j
@Managed
@DamiTopic("account")
public class RegisterServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PasswordEncryptor encryptor;

    public RegisterServiceImpl(
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
     * 注册，注册成功将自动登录
     *
     * @param accountBO   账号信息
     * @param verify_code 验证码
     * @param real_ip     真实IP
     * @return 账号
     * @see AccountBO
     */
    @Transaction
    public Result<String> register(AccountBO accountBO, String verify_code, String real_ip) {
        // 验证码校验
        if (checkVerifyCode(new VerifyCodeKey(accountBO.getEmail(), VerifyCodeType.REGISTER), verify_code))
            return Result.failure("验证码错误");
        // 校验账号可用性
        String accountId = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, accountBO.getEmail()));
        if (accountId != null) return Result.failure("该邮箱已注册");
        // 创建账号
        accountBO = createAccount(accountBO);
        if (accountBO == null) return Result.failure("注册失败");
        // 保存账号
        AccountPO accountPO = BOtoPOConvert.INSTANCE.registerBOToAccountPO(accountBO);
        if (!accountService.save(accountPO)) {
            log.warn("Failed to save account: {}", real_ip);
            return Result.failure("注册失败");
        }
        accountIndexCache.put(new AccountIndexCache(AccountType.ACCOUNT, accountPO.getAccount()), accountPO.getAccount());
        accountCache.put(accountPO.getAccount(), accountPO);
        return Result.succeed(accountPO.getAccount());
    }

    /**
     * 创建账号
     *
     * @param accountBO 账号信息框架
     * @return 创建的完整账号信息
     */
    private AccountBO createAccount(AccountBO accountBO) {
        // 随机生成8位长度的账号
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
        accountBO.setNickName(accountBO.getAccount());
        // 对密码进行哈希
        String salt = RandomStringUtils.generateLowerUpper(32);
        String password = encryptor.encrypt(accountBO.getPassword(), salt);
        accountBO.setPassword(password);
        accountBO.setSalt(salt);
        accountBO.setAlgorithm(encryptor.algorithmName());
        // 基于账号生成固定UUID
        accountBO.setMcUuid(UUID.nameUUIDFromBytes(("Account:" + accountBO.getAccount())
                .getBytes(StandardCharsets.UTF_8)));
        return accountBO;
    }
}
