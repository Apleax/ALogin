package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.AllArgsConstructor;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Valid;
import xyz.apleax.ALogin.Service.BedrockService;
import xyz.apleax.ALogin.VO.BedrockAutoLoginVO;
import xyz.apleax.ALogin.VO.BedrockBindVO;

/**
 * 基岩版相关的内部接口（供 Geyser 扩展调用，密钥即 Premium.assertion.secret）
 *
 * @author Apleax
 */
@Valid
@Controller
@AllArgsConstructor
@Mapping(path = "/api/internal/bedrock", produces = "application/json", consumes = "application/json")
public class BedrockInternal {
    /**
     * 扩展调用时携带的请求头名（值 = Premium.assertion.secret）
     */
    private static final String KEY_HEADER = "X-ALogin-Key";

    private final BedrockService bedrockService;

    @SaIgnore
    @Mapping(path = "/AutoLogin", method = MethodType.POST,
            name = "基岩版免密登录", description = "按 XUID 找到已绑定账号并返回可用的 sa-token，供扩展写入进服 cookie")
    public Result<String> AutoLogin(BedrockAutoLoginVO body, Context context) {
        return bedrockService.autoLogin(context.header(KEY_HEADER), body.getXuid(), body.getName());
    }

    @SaIgnore
    @Mapping(path = "/Bind", method = MethodType.POST,
            name = "基岩版绑定", description = "把 XUID 绑定到刚完成密码登录的账号（token 证明账号、密钥证明调用方）")
    public Result<Boolean> Bind(BedrockBindVO body, Context context) {
        return bedrockService.bind(context.header(KEY_HEADER), body.getToken(), body.getXuid(), body.getName());
    }
}
