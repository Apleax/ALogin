package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.GameProfile;

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
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public GameProfileServiceImpl(
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    @Transaction
    public GameProfile GameProfile(String token, String uuid) {
        String account;
        AccountPO accountPO;
        if (!token.isBlank()) account = (String) StpUtil.getLoginIdByToken(token);
        else account = accountIndexCache.get(new AccountIndexCache(AccountType.UUID, uuid));
        if (account == null) return null;
        accountPO = accountCache.get(account);
        if (accountPO == null) return null;
        List<GameProfile.Property> properties = accountPO.getProperties();
        if (properties == null) properties = Collections.emptyList();
        return new GameProfile(
                accountPO.getMcUuid(),
                accountPO.getNickName(),
                properties);
    }
}
