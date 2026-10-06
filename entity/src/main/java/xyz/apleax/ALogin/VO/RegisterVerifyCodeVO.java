package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * 注册验证码请求
 *
 * @author Apleax
 */
@Data
public class RegisterVerifyCodeVO implements VerifyCodeVO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    String email;
}