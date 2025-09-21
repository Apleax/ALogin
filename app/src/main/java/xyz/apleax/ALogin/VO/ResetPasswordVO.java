package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.Length;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * @author Apleax
 */
@Data
public class ResetPasswordVO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    String email;
    @NotBlank(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码长度必须为6")
    String verify_code;
    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, max = 24, message = "密码长度必须在6-24之间")
    String new_password;
}
