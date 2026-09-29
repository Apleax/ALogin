package xyz.apleax.ALogin.Service.Bedrock;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;

import java.util.Objects;

/**
 * 基岩版（XUID）免密登录与绑定
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("bedrock")
public class BedrockServiceImpl {
    private final PremiumAssertionService assertionService;
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public BedrockServiceImpl(PremiumAssertionService assertionService,
                              @Ds("DataBase") IAccountService accountService,
                              @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                              @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.assertionService = assertionService;
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    /**
     * 已绑定 XUID 的免密登录，返回供扩展写入进服 cookie 的 sa-token
     */
    public Result<String> autoLogin(String key, String xuid, String name) {
        if (!assertionService.matchesSharedKey(key)) return Result.failure("密钥校验失败");
        if (!isValidXuid(xuid)) return Result.failure("XUID 非法");
        String account = accountIndexCache.get(new AccountIndexCache(AccountType.BEDROCK_XUID, xuid));
        if (account == null) return Result.failure("该基岩版账号尚未绑定 ALogin 账号");
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure("账号不存在");
        if (name != null && !name.isBlank() && !Objects.equals(name, accountPO.getBedrockName()))
            updateBedrockName(account, name);
        StpUtil.login(account, AccountType.BEDROCK_XUID.getKey());
        log.info("基岩版免密登录，account={}, xuid={}, name={}", account, xuid, name);
        return Result.succeed(StpUtil.getTokenInfo().getTokenValue());
    }

    /**
     * 把 XUID 绑定到 token 对应的账号（仅扩展调用）
     */
    public Result<Boolean> bind(String key, String token, String xuid, String name) {
        if (!assertionService.matchesSharedKey(key)) return Result.failure("密钥校验失败");
        if (!isValidXuid(xuid)) return Result.failure("XUID 非法");
        Object loginId = token == null || token.isBlank() ? null : StpUtil.getLoginIdByToken(token);
        if (loginId == null) return Result.failure("登录令牌已失效，无法绑定");
        String account = loginId.toString();
        String boundAccount = accountIndexCache.get(new AccountIndexCache(AccountType.BEDROCK_XUID, xuid));
        if (boundAccount != null && !boundAccount.equals(account))
            return Result.failure("该基岩版账号已绑定到其他账号，如需更换请先解绑");
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.failure("账号不存在");
        String previousXuid = accountPO.getBedrockXuid();
        if (xuid.equals(previousXuid)) {
            if (name != null && !name.isBlank() && !Objects.equals(name, accountPO.getBedrockName()))
                updateBedrockName(account, name);
            log.info("基岩版账号重复绑定（幂等），account={}, xuid={}", account, xuid);
            return Result.succeed(true);
        }
        boolean updated;
        try {
            updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getBedrockXuid, xuid)
                    .set(AccountPO::getBedrockName, name)
                    .eq(AccountPO::getAccount, account));
        } catch (RuntimeException e) {
            log.error("绑定基岩版 XUID 异常，account={}, xuid={}", account, xuid, e);
            return Result.failure("绑定失败，请稍后再试或联系管理员");
        }
        if (!updated) {
            log.error("绑定基岩版 XUID 失败，account={}", account);
            return Result.failure("绑定失败，请稍后再试");
        }
        accountCache.invalidate(account);
        accountIndexCache.invalidate(new AccountIndexCache(AccountType.BEDROCK_XUID, xuid));
        if (previousXuid != null)
            accountIndexCache.invalidate(new AccountIndexCache(AccountType.BEDROCK_XUID, previousXuid));
        log.info("基岩版绑定成功，account={}, xuid={}, name={}, previousXuid={}", account, xuid, name, previousXuid);
        return Result.succeed(true);
    }

    /**
     * 同步基岩版展示名称（仅用于展示）
     */
    private void updateBedrockName(String account, String name) {
        if (name == null || name.isBlank()) return;
        try {
            if (accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getBedrockName, name)
                    .eq(AccountPO::getAccount, account))) {
                accountCache.invalidate(account);
                log.info("基岩版名称已同步，account={}, name={}", account, name);
            }
        } catch (RuntimeException e) {
            log.warn("同步基岩版名称异常，account={}: {}", account, e.getMessage());
        }
    }

    /**
     * XUID 是 Xbox 账号的十进制数字 ID
     */
    private static boolean isValidXuid(String xuid) {
        return xuid != null && !xuid.isBlank() && xuid.length() <= 32
                && xuid.chars().allMatch(Character::isDigit);
    }
}
