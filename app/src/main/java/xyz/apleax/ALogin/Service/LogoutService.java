package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("account.logout")
public interface LogoutService {
    /**
     * 登出
     *
     * @author Apleax
     */
    Result<SaTokenInfo> logout(String token);
}
