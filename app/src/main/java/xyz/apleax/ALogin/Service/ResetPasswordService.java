package xyz.apleax.ALogin.Service;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("account.resetpassword")
public interface ResetPasswordService {
    /**
     * 重置密码
     *
     * @param email        邮箱
     * @param verify_code  验证码
     * @param new_password 新密码
     * @author Apleax
     */
    Result<Boolean> resetPassword(String email, String verify_code, String new_password);
}
