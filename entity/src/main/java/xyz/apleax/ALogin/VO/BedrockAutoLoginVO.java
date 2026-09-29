package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * 基岩版免密登录请求（仅 Geyser 扩展携带密钥调用）
 *
 * @author Apleax
 */
@Data
public class BedrockAutoLoginVO {
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
