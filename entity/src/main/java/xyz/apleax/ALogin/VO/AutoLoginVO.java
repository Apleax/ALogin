package xyz.apleax.ALogin.VO;

import lombok.Data;
import org.noear.solon.validation.annotation.NotBlank;
import org.noear.solon.validation.annotation.NotNull;

/**
 * 开关自动登录请求体。enabled 用包装类型：缺字段必须报错，不能静默当成 false。
 *
 * @author Apleax
 */
@Data
public class AutoLoginVO {
    /**
     * 绑定类型，见 {@link xyz.apleax.ALogin.Enum.BindType}
     */
    @NotBlank(message = "绑定类型不能为空")
    private String type;
    /**
     * 是否开启自动登录
     */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
