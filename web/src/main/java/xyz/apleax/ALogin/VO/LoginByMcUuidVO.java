package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.Length;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * @author Apleax
 */
@Data
public class LoginByMcUuidVO implements LoginVO {
    @NotBlank(message = "mc_uuid不能为空")
    private String mc_uuid;
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 24, message = "密码长度必须在6-24之间")
    private String password;
    @NotBlank(message = "登录IP不能为空")
    private String login_ip;
}
