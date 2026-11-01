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
import xyz.apleax.ALogin.Enum.LoginDevice;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.ClientIp;
import xyz.apleax.ALogin.Util.Encrypt.EncryptorSelector;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;

import java.util.HashMap;
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

    /**
     * 登录；免密快捷路径仅在"会话归属该账号 + 同 IP + 同设备"时生效（防止同 IP 下借他人会话冒充登录，
     * 也避免把网页会话的 token 当作进服凭证复用）。
     * <p>
     * device 是 Sa-Token 的设备（终端）维度，不是登录凭证类型：{@code is-concurrent=false} 时只顶掉同 device
     * 的旧会话。登录方式（email/account/...）写入 Token-Session 的 loginMethod，仅作审计。
     */
    @Transaction
    public Result<SaTokenInfo> login(LoginBO loginBO, String token) {
        if (loginBO == null) return Result.failure("请求参数缺失");
        AccountType accountType = loginBO.getAccount_type();
        if (accountType == null) return Result.failure("登录类型缺失");
        if (accountType == AccountType.UUID) {
            log.debug("拒绝 UUID 登录尝试，realIp={}", loginBO.getReal_ip());
            return Result.failure("暂不支持该登录方式");
        }
        LoginDevice device = loginBO.getDevice() == null ? LoginDevice.WEB : loginBO.getDevice();
        String rawPassword = loginBO.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) return Result.failure("密码不能为空");
        String realIp = loginBO.getReal_ip();
        if (!ClientIp.isValid(realIp)) {
            log.warn("登录请求携带缺失或非法的 real_ip，已拒绝，len={}", realIp == null ? 0 : realIp.length());
            return Result.failure("无法获取客户端 IP，请稍后再试");
        }

        String account = getAccountId(loginBO, accountType);
        if (account == null) {
            log.debug("登录失败：账号不存在，type={}, realIp={}", accountType, realIp);
            return Result.failure(accountType.getValue() + "或密码错误");
        }
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure(accountType.getValue() + "或密码错误");

        String currentLoginId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null;
        if (account.equals(currentLoginId)) {
            String storedIp = accountPO.getLastLoginIp();
            if (storedIp != null && storedIp.equals(realIp) && device.getKey().equals(StpUtil.getLoginDeviceType())) {
                StpUtil.updateLastActiveToNow();
                SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
                dispatchLoginEvent(account, token, tokenInfo.getTokenValue());
                log.info("登录成功，email={}, mcUuid={}, loginMethod={}, device={}（复用当前会话）",
                        accountPO.getEmail(), accountPO.getMcUuid(), accountType.getKey(), device.getKey());
                return Result.succeed(tokenInfo);
            }
            if (storedIp != null && storedIp.equals(realIp)) {
                log.info("当前会话 device={} 与本次登录 device={} 不一致，为 {} 端新建会话，account={}",
                        StpUtil.getLoginDeviceType(), device.getKey(), device.getValue(), account);
            } else {
                log.warn("检测到异地登录，废弃当前 token，account={}, oldIp={}, newIp={}",
                        account, storedIp, realIp);
                StpUtil.logout();
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

        if (device == LoginDevice.BEDROCK) {
            // 基岩版这次请求由 Geyser 扩展代发（device 只有在 X-ALogin-Key 校验通过时才会是 BEDROCK），
            // 因此 real_ip 是扩展所在机器而不是玩家：写进 lastLoginIp 会污染数据，
            // 也会给"同 IP 复用当前会话"的免密快捷路径一个假判据，这里只记日志不落库
            log.info("基岩版登录由扩展代发，跳过 lastLoginIp 更新，account={}, real_ip={}", account, realIp);
        } else {
            boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, realIp)
                    .eq(AccountPO::getAccount, account));
            if (!updated) {
                log.error("更新最后登录 IP 失败，拒绝登录，account={}", account);
                return Result.failure("登录失败，请稍后再试");
            }
            accountCache.invalidate(account);
        }

        StpUtil.login(account, device.getKey());
        StpUtil.getTokenSession().set("loginMethod", accountType.getKey());
        log.info("登录成功，email={}, mcUuid={}, loginMethod={}, device={}",
                accountPO.getEmail(), accountPO.getMcUuid(), accountType.getKey(), device.getKey());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 本次登录刚生成的 token 随事件下发，避免下游按 account 反查"最后一个 token"取错端
        dispatchLoginEvent(account, token, tokenInfo.getTokenValue());
        return Result.succeed(tokenInfo);
    }

    private void dispatchLoginEvent(String account, String token, String sessionToken) {
        if (token == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("account", account);
        payload.put("token", token);
        if (sessionToken != null && !sessionToken.isBlank()) payload.put("sessionToken", sessionToken);
        Dami.bus().send("LoginEvent", payload);
    }

    private String getAccountId(LoginBO loginBO, AccountType accountType) {
        String identifier = switch (accountType) {
            case EMAIL -> loginBO.getEmail();
            case ACCOUNT -> loginBO.getAccount();
            case QQ_ACCOUNT -> loginBO.getQq_account();
            case UUID, PREMIUM_UUID, BEDROCK_XUID -> null;
        };
        if (identifier == null || identifier.isBlank()) return null;
        return accountIndexCache.get(new AccountIndexCache(accountType, identifier));
    }
}
