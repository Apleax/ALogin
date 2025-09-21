package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.Length;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * @author Apleax
 */
@Data
public class LoginByAccountVO implements LoginVO {
    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    @Length(min = 8, max = 8, message = "账号长度为8位")
    private String account;
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 24, message = "密码长度必须在6-24之间")
    private String password;
}
