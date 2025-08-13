package xyz.apleax.ALogin.Entity.DTO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * @author Apleax
 */
@Data
public class LoginByMcUuidDTO {
    @NotBlank(message = "mc_uuid不能为空")
    private String mc_uuid;
    private String password;
    @NotBlank(message = "最后登录IP不能为空")
    private String last_login_ip;
}
