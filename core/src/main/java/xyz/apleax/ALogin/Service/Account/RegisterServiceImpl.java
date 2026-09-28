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
import xyz.apleax.ALogin.PO.LibreLoginPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.SQL.Mapper.LibreLoginMapper;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
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
    private static final int ACCOUNT_GEN_MAX_ATTEMPTS = 20;
    private static final DateTimeFormatter JOINED_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.S]");

    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PasswordEncryptor encryptor;
    private final LibreLoginMapper libreLoginMapper;

    public RegisterServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
            @Inject("Algorithm") PasswordEncryptor encryptor,
            @Ds("DataBase") LibreLoginMapper libreLoginMapper) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.encryptor = encryptor;
        this.libreLoginMapper = libreLoginMapper;
    }

    /**
     * 注册：注册成功后返回账号
     *
     * @param accountBO   账号信息
     * @param verify_code 验证码
     * @param real_ip     IP（仅用于日志，不写入 lastLoginIp）
     * @return 新账号 ID
     * @see AccountBO
     */
    @Transaction
    public Result<String> register(AccountBO accountBO, String verify_code, String real_ip) {
        if (accountBO == null) return Result.failure("请求参数缺失");
        String email = accountBO.getEmail();
        if (email == null || email.isBlank()) return Result.failure("邮箱不能为空");
        if (accountBO.getPassword() == null || accountBO.getPassword().isEmpty()) return Result.failure("密码不能为空");

        if (checkVerifyCode(new VerifyCodeKey(email, VerifyCodeType.REGISTER), verify_code))
            return Result.failure("验证码错误");

        String existing = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, email));
        if (existing != null) return Result.failure("该邮箱已注册");

        if (!populateAccount(accountBO)) return Result.failure("注册失败，请稍后再试");
        AccountPO accountPO = BOtoPOConvert.INSTANCE.registerBOToAccountPO(accountBO);

        try {
            if (!accountService.save(accountPO)) {
                log.warn("保存账号失败（未知原因），email={}, realIp={}", email, real_ip);
                return Result.failure("注册失败，请稍后再试");
            }
        } catch (Exception e) {
            if (isDuplicateKey(e)) {
                log.warn("注册冲突（UNIQUE 索引），email={}, msg={}", email, e.getMessage());
                return Result.failure("该邮箱已注册");
            }
            log.error("注册时发生未预期异常，email={}", email, e);
            throw e;  // 让事务回滚
        }

        String account = accountPO.getAccount();
        accountIndexCache.invalidate(new AccountIndexCache(AccountType.ACCOUNT, account));
        accountIndexCache.invalidate(new AccountIndexCache(AccountType.EMAIL, email));
        accountCache.invalidate(account);
        log.info("注册成功，account={}, email={}, mcUuid={}", account, email, accountBO.getMcUuid());
        return Result.succeed(account);
    }

    /**
     * 组装账号数据：生成唯一 account、从 LibreLogin 迁移历史数据、加密密码。
     *
     * @return 是否成功
     */
    private boolean populateAccount(AccountBO accountBO) {
        String account = generateUniqueAccount();
        if (account == null) {
            log.warn("生成唯一账号失败，重试 {} 次后放弃", ACCOUNT_GEN_MAX_ATTEMPTS);
            return false;
        }
        accountBO.setAccount(account);

        // 从 LibreLogin 迁移数据（若存在同邮箱记录）
        List<LibreLoginPO> libreLoginPOS = libreLoginMapper.selectList(
                new LambdaQueryWrapper<LibreLoginPO>()
                        .eq(LibreLoginPO::getEmail, accountBO.getEmail())
                        .orderByAsc(LibreLoginPO::getJoined));
        if (!libreLoginPOS.isEmpty()) {
            LibreLoginPO first = libreLoginPOS.getFirst();
            // LibreLogin 表中 joined 为 MySQL timestamp，转换为秒级时间戳
            accountBO.setRegistrationTime(parseJoinedTimestamp(first.getJoined()));
            accountBO.setMcUuid(first.getUuid());
            accountBO.setNickName(first.getLastNickname());
        } else {
            accountBO.setRegistrationTime(System.currentTimeMillis() / 1000);
            accountBO.setNickName(account);
            // 基于账号生成固定 UUID
            accountBO.setMcUuid(UUID.nameUUIDFromBytes(("Account:" + account)
                    .getBytes(StandardCharsets.UTF_8)));
        }

        // 加密密码
        String salt = RandomStringUtils.generateLowerUpper(32);
        accountBO.setPassword(encryptor.encrypt(accountBO.getPassword(), salt));
        accountBO.setSalt(salt);
        accountBO.setAlgorithm(encryptor.algorithmName());
        return true;
    }

    /**
     * 生成一个 8 位数字、首位非 0、且在 数据库 中未被占用的账号。
     *
     * @return 生成的账号；重试次数用尽时返回 null
     */
    private String generateUniqueAccount() {
        for (int i = 0; i < ACCOUNT_GEN_MAX_ATTEMPTS; i++) {
            String candidate = RandomStringUtils.generateNum(8);
            // 首位为 0 会破坏账号格式一致性，跳过
            if (candidate.charAt(0) == '0') continue;
            // 已存在则跳过；并发情况下 DB UNIQUE 索引兜底
            if (accountService.exists(new LambdaQueryWrapper<AccountPO>()
                    .select(AccountPO::getAccount)
                    .eq(AccountPO::getAccount, candidate))) continue;
            return candidate;
        }
        return null;
    }

    /**
     * 判断异常链中是否包含 SQL 完整性约束冲突（SQLState 23xxx）
     */
    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause())
            if (c instanceof SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if (state != null && state.startsWith("23")) return true;
            }
        return false;
    }

    /**
     * 将 LibreLogin 表中 MySQL timestamp 格式的时间字符串转换为秒级时间戳。
     *
     * @param joined joined 字段，形如 "yyyy-MM-dd HH:mm:ss"
     * @return 秒级时间戳，解析失败时返回当前时间戳
     */
    private Long parseJoinedTimestamp(String joined) {
        if (joined == null || joined.isBlank()) return System.currentTimeMillis() / 1000;
        try {
            return LocalDateTime.parse(joined, JOINED_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .getEpochSecond();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse LibreLogin joined time: {}", joined);
            return System.currentTimeMillis() / 1000;
        }
    }
}
