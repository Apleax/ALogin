package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.SQL.Service.IAccountService;

import java.util.List;
import java.util.regex.Pattern;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class ChangeNickNameServiceImpl {
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final long CHANGE_NICK_NAME_INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;

    public ChangeNickNameServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache) {
        this.accountService = accountService;
        this.accountCache = accountCache;
    }

    @Transaction
    public Result<Boolean> changeNickName(String nickname) {
        String account = StpUtil.getLoginIdAsString();
        if (account == null) return Result.succeed(false);
        if (nickname == null || !NICKNAME_PATTERN.matcher(nickname).matches())
            return Result.failure("用户名只能包含英文字母、数字和下划线，长度需为3-16个字符", false);
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.succeed(false);

        String oldNickName = accountPO.getNickName();
        if (nickname.equals(oldNickName)) return Result.succeed(true);

        long now = System.currentTimeMillis();

        List<AccountPO> previousHolders = accountService.list(new LambdaQueryWrapper<AccountPO>()
                .select(AccountPO::getLastChangeNickNameTime)
                .eq(AccountPO::getPreviousName, nickname)
                .isNotNull(AccountPO::getLastChangeNickNameTime));
        long latestAbandonTime = previousHolders.stream()
                .mapToLong(AccountPO::getLastChangeNickNameTime)
                .max().orElse(0L);
        if (latestAbandonTime > 0L) {
            long interval = now - latestAbandonTime;
            if (interval < CHANGE_NICK_NAME_INTERVAL_MILLIS) {
                long remainDays = ceilDays(CHANGE_NICK_NAME_INTERVAL_MILLIS - interval);
                return Result.failure(remainDays + "日后可以再次使用该昵称", false);
            }
        }

        Long selfLastChange = accountPO.getLastChangeNickNameTime();
        if (selfLastChange != null) {
            long interval = now - selfLastChange;
            if (interval < CHANGE_NICK_NAME_INTERVAL_MILLIS) {
                long remainDays = ceilDays(CHANGE_NICK_NAME_INTERVAL_MILLIS - interval);
                return Result.failure(remainDays + "日后可以再次更改昵称", false);
            }
        }

        boolean isNicknameUsed = accountService.exists(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getNickName, nickname)
                .ne(AccountPO::getAccount, account));
        if (isNicknameUsed) {
            log.debug("用户名 [{}] 已被使用，用户: {}", nickname, account);
            return Result.failure("该用户名已被使用，请更换其他用户名", false);
        }

        boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                .eq(AccountPO::getAccount, account)
                .set(AccountPO::getNickName, nickname)
                .set(AccountPO::getPreviousName, oldNickName)
                .set(AccountPO::getLastChangeNickNameTime, now));
        if (!updated) return Result.succeed(false);

        accountService.update(new LambdaUpdateWrapper<AccountPO>()
                .eq(AccountPO::getPreviousName, nickname)
                .ne(AccountPO::getAccount, account)
                .set(AccountPO::getPreviousName, null));
        
        accountCache.invalidate(account);
        log.debug("修改用户名成功，用户: {}，{} to {}", account, oldNickName, nickname);
        return Result.succeed(true);
    }

    /**
     * 毫秒数向上取整为天，避免 Duration.toDays() 在 24h 整数倍边界上的截断偏差。
     */
    private static long ceilDays(long millis) {
        if (millis <= 0L) return 0L;
        long dayMillis = 24L * 60 * 60 * 1000;
        return (millis + dayMillis - 1) / dayMillis;
    }
}
