package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;

/**
 * 账号Service
 *
 * @author Apleax
 */
@DamiTopic("account")
public interface AccountService {
    /**
     * 注册
     *
     * @param accountBO   注册信息
     * @param verify_code 验证码
     * @param real_ip     真实ip
     * @author Apleax
     */
    Result<SaTokenInfo> register(AccountBO accountBO, String verify_code, String real_ip);

    /**
     * 登录
     *
     * @param loginBO 登录信息
     * @author Apleax
     */
    Result<SaTokenInfo> login(LoginBO loginBO, String token);

    /**
     * 登出
     *
     * @author Apleax
     */
    Result<SaTokenInfo> logout(String token);

    /**
     * 重置密码
     *
     * @param email        邮箱
     * @param verify_code  验证码
     * @param new_password 新密码
     * @author Apleax
     */
    Result<Boolean> resetPassword(String email, String verify_code, String new_password);

    /**
     * 邮箱验证码
     *
     * @param verifyCodeKey 验证码参数
     * @author Apleax
     */
    Result<Long> verifyCode(VerifyCodeKey verifyCodeKey);
}
