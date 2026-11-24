package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;

/**
 * 解绑请求：用 type 区分解绑正版（java）还是基岩版（bedrock）
 *
 * @author Apleax
 */
@Data
public class UnbindVO {
    /**
     * 绑定类型，见 {@link xyz.apleax.ALogin.Enum.BindType}
     */
    @NotBlank(message = "绑定类型不能为空")
    private String type;
}
