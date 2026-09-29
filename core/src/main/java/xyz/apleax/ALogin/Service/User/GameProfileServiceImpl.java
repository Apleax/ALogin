package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.SQL.Service.IAccountService;

import java.util.Collections;
import java.util.List;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class GameProfileServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public GameProfileServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    public GameProfile GameProfile(String token, String uuid, String playerIp) {
        String account;
        AccountPO accountPO;
        if (!token.isBlank()) account = (String) StpUtil.getLoginIdByToken(token);
        else account = accountIndexCache.get(new AccountIndexCache(AccountType.UUID, uuid));
        if (account == null) return null;
        accountPO = accountCache.get(account);
        if (accountPO == null) return null;
        if (!token.isBlank()) updateLastLoginIp(account, playerIp);
        List<GameProfile.Property> properties = accountPO.getProperties();
        if (properties == null) properties = Collections.emptyList();
        return new GameProfile(
                accountPO.getMcUuid(),
                accountPO.getNickName(),
                properties);
    }

    // 入口代理上报的真实 IP：仅 token 分支可信，尽力更新且不影响取档案
    private void updateLastLoginIp(String account, String playerIp) {
        if (playerIp == null) return;
        String ip = playerIp.trim();
        if (ip.isEmpty() || ip.length() > 45 || ip.indexOf('/') >= 0
                || ip.chars().anyMatch(Character::isWhitespace)) {
            log.warn("忽略格式非法的上报 IP，account={}, len={}", account, playerIp.length());
            return;
        }
        try {
            boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, ip)
                    .eq(AccountPO::getAccount, account));
            if (updated) accountCache.invalidate(account);
            else log.warn("更新最后登录 IP 失败(上报)，account={}", account);
        } catch (Exception e) {
            log.warn("更新最后登录 IP 异常(上报)，account={}: {}", account, e.getMessage());
        }
    }
}
