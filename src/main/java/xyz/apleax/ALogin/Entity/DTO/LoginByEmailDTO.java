package xyz.apleax.ALogin.Entity.DTO;

import lombok.Data;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.Length;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * @author Apleax
 */
@Data
public class LoginByEmailDTO {
    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 24, message = "密码长度必须在6-24之间")
    private String password;
    /**
     * 登录来源
     */
    @NotBlank(message = "登录来源不能为空")
    private String login_type;
}
