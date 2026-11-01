package xyz.apleax.ALogin.Service.Remote;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;

/**
 * 账号远程服务契约（Dami LPC topic: account）
 *
 * @author Apleax
 */
@DamiTopic("account")
public interface AccountService {
    /**
     * 注册（注册后不自动登录，返回新账号 ID）
     *
     * @param accountBO   注册信息
     * @param verify_code 验证码
     * @param real_ip     真实ip
     */
    Result<String> register(AccountBO accountBO, String verify_code, String real_ip);

    /**
     * 登录
     *
     * @param loginBO 登录信息
     * @param token   进服会话临时 token（可为空）
     */
    Result<SaTokenInfo> login(LoginBO loginBO, String token);

    /**
     * 登出
     *
     * @param token 保留参数（登出基于会话）
     */
    Result<Void> logout(String token);

    /**
     * 重置密码
     *
     * @param email        邮箱
     * @param verify_code  验证码
     * @param new_password 新密码
     */
    Result<Boolean> resetPassword(String email, String verify_code, String new_password);

    /**
     * 邮箱验证码
     *
     * @param verifyCodeKey 验证码参数
     */
    Result<Long> verifyCode(VerifyCodeKey verifyCodeKey);
}
