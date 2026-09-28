package xyz.apleax.ALogin.Service.Account;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Transaction;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("account")
public class LogoutServiceImpl {
    @Transaction
    public Result<Void> logout(String token) {
        String account = StpUtil.getLoginIdAsString();
        StpUtil.logout();
        log.info("登出成功，account={}", account);
        return Result.succeed();
    }
}
