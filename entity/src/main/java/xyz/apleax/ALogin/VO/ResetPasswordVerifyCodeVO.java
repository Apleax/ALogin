package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * 重置密码验证码请求
 *
 * @author Apleax
 */
@Data
public class ResetPasswordVerifyCodeVO implements VerifyCodeVO {
    @NotBlank(message = "邮箱不能为空")
    String email;
}