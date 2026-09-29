package xyz.apleax.ALogin.Service.Premium;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.Dami;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.PremiumDeviceCode;
import xyz.apleax.ALogin.POJO.PremiumFlowStatus;
import xyz.apleax.ALogin.POJO.PremiumProfile;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.MailUtil;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 正版（微软 OAuth）绑定与一键登录
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("premium")
public class PremiumServiceImpl {
    private static final long AUTO_LOGIN_TOKEN_TTL_SECONDS = 60;
    /**
     * 设备码授权流程状态（过期自动清理）
     */
    private static final Cache<String, Flow> FLOWS = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(20))
            .build();
    private static final ExecutorService FLOW_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final PremiumOAuthService oauthService;
    private final PremiumAssertionService assertionService;
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public PremiumServiceImpl(PremiumOAuthService oauthService,
                              PremiumAssertionService assertionService,
                              @Ds("DataBase") IAccountService accountService,
                              @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                              @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.oauthService = oauthService;
        this.assertionService = assertionService;
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    /**
     * 发起绑定正版账号的设备码授权（需网页已登录）
     */
    public Result<PremiumDeviceCode> getBindCode() {
        return startFlow("bind", StpUtil.getLoginIdAsString(), null, null);
    }

    /**
     * 发起"微软账号一键进入"的设备码授权
     *
     * @param token  进服会话临时 token
     * @param realIp 来源 IP
     */
    public Result<PremiumDeviceCode> getLoginCode(String token, String realIp) {
        if (token == null || token.isBlank() || SaTempUtil.parseToken(token, UUID.class) == null)
            return Result.failure("会话已过期，请重新进入服务器");
        return startFlow("login", null, token, realIp);
    }

    /**
     * 已绑定正版 UUID 名单（换行分隔）
     */
    public Result<String> getBoundUuids(String key) {
        if (!assertionService.matchesSharedKey(key)) return Result.failure("密钥校验失败");
        List<AccountPO> accounts = accountService.list(new LambdaQueryWrapper<AccountPO>()
                .select(AccountPO::getPremiumUuid)
                .isNotNull(AccountPO::getPremiumUuid));
        String joined = accounts.stream()
                .map(AccountPO::getPremiumUuid)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .collect(Collectors.joining("\n"));
        return Result.succeed(joined);
    }

    /**
     * 查询授权流程状态（前端轮询用）
     */
    public Result<PremiumFlowStatus> getStatus(String flowKey) {
        Flow flow = FLOWS.getIfPresent(flowKey);
        if (flow == null) return Result.failure("授权会话不存在或已过期，请重新发起");
        return Result.succeed(new PremiumFlowStatus(flow.status, flow.message, flow.name));
    }

    /**
     * 发起一次设备码授权
     */
    private Result<PremiumDeviceCode> startFlow(String type, String account, String gameToken, String realIp) {
        Result<PremiumOAuthService.DeviceCode> started = oauthService.startDeviceCode();
        if (started.getCode() != Result.SUCCEED_CODE) return Result.failure(started.getDescription());
        PremiumOAuthService.DeviceCode deviceCode = started.getData();

        String flowKey = RandomStringUtils.generateLowerUpper(32);
        Flow flow = new Flow(type, account, gameToken, realIp, deviceCode.deviceCode());
        FLOWS.put(flowKey, flow);
        FLOW_EXECUTOR.submit(() -> pollUntilDone(flow, deviceCode));
        log.info("已发起正版设备码授权，type={}, account={}, 有效期={}秒",
                type, account, deviceCode.expiresInSeconds());
        return Result.succeed(new PremiumDeviceCode(flowKey, deviceCode.userCode(),
                deviceCode.verificationUri(), deviceCode.expiresInSeconds()));
    }

    private void pollUntilDone(Flow flow, PremiumOAuthService.DeviceCode deviceCode) {
        long deadline = System.currentTimeMillis() + deviceCode.expiresInSeconds() * 1000L;
        long intervalMillis = deviceCode.intervalSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline && PremiumFlowStatus.PENDING.equals(flow.status)) {
            PremiumOAuthService.DevicePoll polled = oauthService.pollDeviceCode(flow.deviceCode);
            switch (polled.status()) {
                case PENDING -> intervalMillis = deviceCode.intervalSeconds() * 1000L;
                case SLOW_DOWN -> intervalMillis += 5_000L;
                case SUCCESS -> {
                    complete(flow, polled.microsoftToken());
                    return;
                }
                case FAILED -> {
                    flow.fail(polled.userMessage());
                    return;
                }
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (PremiumFlowStatus.PENDING.equals(flow.status)) flow.fail("授权超时，请重新发起");
    }

    /**
     * 授权成功后的收尾
     */
    private void complete(Flow flow, String microsoftToken) {
        Result<PremiumProfile> resolved = oauthService.resolveProfile(microsoftToken);
        if (resolved.getCode() != Result.SUCCEED_CODE) {
            flow.fail(resolved.getDescription());
            return;
        }
        PremiumProfile profile = resolved.getData();
        Result<PremiumProfile> done = "bind".equals(flow.type)
                ? bind(flow.account, profile)
                : login(flow.gameToken, profile, flow.realIp);
        if (done.getCode() != Result.SUCCEED_CODE) {
            flow.fail(done.getDescription());
            return;
        }
        flow.succeed(profile.name());
    }

    /**
     * 绑定：把正版 UUID 写入账号（幂等；已被其他账号绑定则拒绝）
     */
    public Result<PremiumProfile> bind(String account, PremiumProfile profile) {
        String boundAccount = accountIndexCache.get(
                new AccountIndexCache(AccountType.PREMIUM_UUID, profile.uuid().toString()));
        if (boundAccount != null && !boundAccount.equals(account))
            return Result.failure("该正版账号已绑定到其他账号，如需更换请先解绑");
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure("账号不存在");
        UUID previousUuid = accountPO.getPremiumUuid();
        if (profile.uuid().equals(previousUuid)) {
            if (!Objects.equals(profile.name(), accountPO.getPremiumName())) {
                if (updatePremiumName(account, profile.name()))
                    log.info("正版账号重复绑定（幂等），补写正版名称，account={}, name={}", account, profile.name());
                else log.warn("正版账号重复绑定（幂等），正版名称补写失败，account={}", account);
            } else log.info("正版账号重复绑定（幂等），account={}, premiumUuid={}", account, profile.uuid());
            return Result.succeed(profile);
        }
        boolean updated;
        try {
            updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getPremiumUuid, profile.uuid().toString())
                    .set(AccountPO::getPremiumName, profile.name())
                    .eq(AccountPO::getAccount, account));
        } catch (RuntimeException e) {
            log.error("绑定正版 UUID 异常，account={}, premiumUuid={}", account, profile.uuid(), e);
            return Result.failure("绑定失败，请稍后再试或联系管理员");
        }
        if (!updated) {
            log.error("绑定正版 UUID 失败，account={}", account);
            return Result.failure("绑定失败，请稍后再试");
        }
        accountCache.invalidate(account);
        accountIndexCache.invalidate(new AccountIndexCache(AccountType.PREMIUM_UUID, profile.uuid().toString()));
        if (previousUuid != null)
            accountIndexCache.invalidate(new AccountIndexCache(AccountType.PREMIUM_UUID, previousUuid.toString()));
        log.info("正版绑定成功，account={}, premiumUuid={}, previousPremiumUuid={}, name={}",
                account, profile.uuid(), previousUuid, profile.name());
        return Result.succeed(profile);
    }

    /**
     * 同步正版名称（仅用于展示）
     */
    private boolean updatePremiumName(String account, String name) {
        if (name == null || name.isBlank()) return false;
        try {
            boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getPremiumName, name)
                    .eq(AccountPO::getAccount, account));
            if (updated) accountCache.invalidate(account);
            return updated;
        } catch (RuntimeException e) {
            log.warn("更新正版名称异常，account={}: {}", account, e.getMessage());
            return false;
        }
    }

    /**
     * 一键登录：按正版 UUID 找到账号并通知登录服放行
     */
    private Result<PremiumProfile> login(String token, PremiumProfile profile, String realIp) {
        if (realIp == null || realIp.isBlank()) return Result.failure("无法获取客户端 IP，请稍后再试");
        String account = accountIndexCache.get(
                new AccountIndexCache(AccountType.PREMIUM_UUID, profile.uuid().toString()));
        if (account == null) return Result.failure("该正版账号尚未绑定 ALogin 账号，请先在网页登录后绑定");
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure("账号不存在");
        return completeLogin(account, accountPO, realIp, token, profile);
    }

    /**
     * 自动登录：由 PremiumAutoLoginListener 调用，dami 线程置登录态需 mock 上下文
     */
    public Result<PremiumProfile> autoLogin(String account, UUID playerUuid, PremiumProfile profile, String realIp) {
        String boundAccount = accountIndexCache.get(
                new AccountIndexCache(AccountType.PREMIUM_UUID, profile.uuid().toString()));
        if (account == null || !account.equals(boundAccount)) {
            log.warn("正版自动登录被拒：绑定关系不匹配，account={}, premiumUuid={}", account, profile.uuid());
            return Result.failure("该正版账号尚未绑定 ALogin 账号");
        }
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure("账号不存在");
        String token = SaTempUtil.createToken(playerUuid, AUTO_LOGIN_TOKEN_TTL_SECONDS, true);
        log.info("正版自动登录，account={}, premiumUuid={}, name={}, ip={}",
                account, profile.uuid(), profile.name(), realIp);
        SaTokenContextMockUtil.setMockContext();
        try {
            return completeLogin(account, accountPO, realIp, token, profile);
        } finally {
            SaTokenContextMockUtil.clearContext();
        }
    }

    /**
     * 正版登录收尾：记录 IP/名称、置登录态并触发 LoginEvent 放行
     */
    private Result<PremiumProfile> completeLogin(String account, AccountPO accountPO, String realIp,
                                                 String token, PremiumProfile profile) {
        if (!Objects.equals(profile.name(), accountPO.getPremiumName())
                && updatePremiumName(account, profile.name()))
            log.info("正版名称变更已同步，account={}, premiumName={}", account, profile.name());
        String oldIp = accountPO.getLastLoginIp();
        if (realIp != null && !realIp.isBlank()) {
            boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, realIp)
                    .eq(AccountPO::getAccount, account));
            if (!updated) {
                log.error("更新最后登录 IP 失败，拒绝正版登录，account={}", account);
                return Result.failure("登录失败，请稍后再试");
            }
            accountCache.invalidate(account);
        }
        StpUtil.login(account, AccountType.PREMIUM_UUID.getKey());
        if (realIp != null && !realIp.isBlank() && oldIp != null && !oldIp.equals(realIp))
            MailUtil.sendIpChangeAlertAsync(accountPO.getEmail(), account, oldIp, realIp);
        Dami.bus().send("LoginEvent", Map.of("account", account, "token", token));
        log.info("正版登录成功，account={}, premiumUuid={}, name={}", account, profile.uuid(), profile.name());
        return Result.succeed(profile);
    }

    /**
     * 一次设备码授权流程
     */
    private static final class Flow {
        private final String type;
        private final String account;
        private final String gameToken;
        private final String realIp;
        /**
         * 原始 device_code，不下发前端
         */
        private final String deviceCode;
        private volatile String status = PremiumFlowStatus.PENDING;
        private volatile String message;
        private volatile String name;

        private Flow(String type, String account, String gameToken, String realIp, String deviceCode) {
            this.type = type;
            this.account = account;
            this.gameToken = gameToken;
            this.realIp = realIp;
            this.deviceCode = deviceCode;
        }

        private void succeed(String name) {
            this.name = name;
            this.status = PremiumFlowStatus.SUCCESS;
        }

        private void fail(String message) {
            this.message = message == null || message.isBlank() ? "授权失败，请重试" : message;
            this.status = PremiumFlowStatus.ERROR;
        }
    }
}
