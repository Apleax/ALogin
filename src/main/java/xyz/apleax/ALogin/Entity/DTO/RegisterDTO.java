package xyz.apleax.ALogin.Entity.DTO;

import lombok.Data;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.Length;
import org.noear.solon.validation.annotation.NotEmpty;

/**
 * 从前端接收的DTO
 *
 * @author Apleax
 */
@Data
public class RegisterDTO {
    /**
     * 邮箱
     */
    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;
    /**
     * 密码
     */
    @NotEmpty(message = "密码不能为空")
    @Length(min = 6, max = 24, message = "密码长度必须在6-24之间")
    private String password;
    /**
     * 验证码
     */
    @NotEmpty(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码长度必须为6")
    private String verify_code;
}
