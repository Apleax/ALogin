package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.NotBlank;
import xyz.apleax.ALogin.Enum.VerifyCodeType;

/**
 * @author Apleax
 */
@Data
public class ResetPasswordVerifyCodeVO implements VerifyCodeVO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    String email;
    VerifyCodeType type = VerifyCodeType.RESET_PASSWORD;
}