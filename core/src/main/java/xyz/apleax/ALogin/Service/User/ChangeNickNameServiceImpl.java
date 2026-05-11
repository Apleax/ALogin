package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
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

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class ChangeNickNameServiceImpl {
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;

    public ChangeNickNameServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache) {
        this.accountService = accountService;
        this.accountCache = accountCache;
    }

    @Transaction
    @SuppressWarnings("unchecked")
    public Result<Boolean> changeNickName(String nickname) {
        String account = StpUtil.getLoginIdAsString();
        if (account == null) return Result.succeed(false);
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.succeed(false);

        if (accountPO.getNickName() != null && accountPO.getNickName().equals(nickname)) return Result.succeed(true);

        boolean isNicknameUsed = accountCache.asMap().values().stream()
                .anyMatch(po -> po.getNickName() != null
                        && po.getNickName().equals(nickname)
                        && !po.getAccount().equals(account));

        if (isNicknameUsed) {
            log.debug("用户名 [{}] 已被使用（缓存检测），用户: {}", nickname, account);
            return Result.failure("该用户名已被使用，请更换其他用户名", false);
        }

        try {
            boolean updated = accountService.update(new LambdaUpdateWrapper<AccountPO>()
                    .eq(AccountPO::getAccount, account).set(AccountPO::getNickName, nickname));
            if (!updated) return Result.succeed(false);
            accountPO.setNickName(nickname);
            accountCache.put(account, accountPO);
            log.debug("修改用户名成功，用户: {}，{} to {}", account, accountPO.getNickName(), nickname);
            return Result.succeed(true);
        } catch (UndeclaredThrowableException e) {
            Throwable exception = ExceptionUtil
                    .getCausedBy(e, SQLIntegrityConstraintViolationException.class);
            if (exception.getMessage() != null && exception.getMessage().contains("Duplicate entry")) {
                log.debug("用户名 [{}] 已被使用，用户: {}", nickname, account);
                return Result.failure("该用户名已被使用，请更换其他用户名", false);
            }
            log.debug("修改用户名失败，用户: {}", account, e);
            return Result.failure("修改用户名失败，请稍后重试", false);
        }
    }
}
