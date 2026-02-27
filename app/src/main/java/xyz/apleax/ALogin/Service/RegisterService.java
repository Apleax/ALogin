package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.BO.AccountBO;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("account.register")
public interface RegisterService {
    /**
     * 注册
     *
     * @param accountBO   注册信息
     * @param verify_code 验证码
     * @param real_ip     真实ip
     * @author Apleax
     */
    Result<SaTokenInfo> register(AccountBO accountBO, String verify_code, String real_ip);
}
