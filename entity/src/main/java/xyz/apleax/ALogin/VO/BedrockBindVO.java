package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * 基岩版绑定请求（仅 Geyser 扩展携带密钥调用）
 *
 * @author Apleax
 */
@Data
public class BedrockBindVO {
    /**
     * 刚完成的密码登录返回的 sa-token，用于确定要绑定的账号
     */
    @NotBlank(message = "登录令牌不能为空")
    private String token;
    /**
     * Xbox 账号的 XUID
     */
    @NotBlank(message = "XUID 不能为空")
    private String xuid;
    /**
     * 基岩版游戏名（用于展示，可为空）
     */
    private String name;
}
