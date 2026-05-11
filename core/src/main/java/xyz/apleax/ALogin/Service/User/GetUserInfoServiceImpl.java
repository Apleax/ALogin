package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.VO.UserInfoVO;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class GetUserInfoServiceImpl {
    private final LoadingCache<@NotNull String, AccountPO> accountCache;

    public GetUserInfoServiceImpl(
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache) {
        this.accountCache = accountCache;
    }

    @Transaction
    public Result<UserInfoVO> getUserInfo() {
        String account = StpUtil.getLoginIdAsString();
        if (account == null) return null;
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return null;
        return Result.succeed(new UserInfoVO(accountPO.getAccount(), accountPO.getNickName(), accountPO.getAvatar()));
    }
}
