package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.AllArgsConstructor;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.Enum.BindType;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;
import xyz.apleax.ALogin.POJO.PremiumDeviceCode;
import xyz.apleax.ALogin.POJO.PremiumFlowStatus;
import xyz.apleax.ALogin.Service.Remote.BedrockService;
import xyz.apleax.ALogin.Service.Remote.PremiumService;
import xyz.apleax.ALogin.Util.ApiResults;
import xyz.apleax.ALogin.VO.UnbindVO;

/**
 * 正版（微软设备码授权）Controller
 *
 * @author Apleax
 */
@Valid
@Controller
@AllArgsConstructor
@Mapping(path = "/api/web/premium")
public class Premium {
    private final PremiumService premiumService;
    private final BedrockService bedrockService;

    @SaIgnore
    @Mapping(path = "/LoginCode", method = MethodType.GET,
            name = "正版一键进入授权", description = "发起微软设备码授权，返回用户码供玩家在任意设备上输入")
    public Result<PremiumDeviceCode> LoginCode(String token, Context context) {
        if (token == null || token.isBlank()) throw ApiException.of(ErrorCode.GAME_TOKEN_MISSING);
        return ApiResults.ok(premiumService.getLoginCode(token, context.realIp()));
    }

    @Mapping(path = "/BindCode", method = MethodType.GET,
            name = "正版绑定授权", description = "发起微软设备码授权，返回用户码供玩家在任意设备上输入")
    public Result<PremiumDeviceCode> BindCode() {
        return ApiResults.ok(premiumService.getBindCode());
    }

    @SaIgnore
    @Mapping(path = "/Status", method = MethodType.GET,
            name = "正版授权状态", description = "轮询设备码授权结果：pending/success/error")
    public Result<PremiumFlowStatus> Status(String flow) {
        if (flow == null || flow.isBlank()) throw ApiException.of(ErrorCode.PARAM_MISSING);
        return ApiResults.ok(premiumService.getStatus(flow));
    }

    @Mapping(path = "/Unbind", method = MethodType.POST,
            name = "解绑正版账号", description = "解绑当前登录账号的正版绑定；type=java 解绑 Java 正版，type=bedrock 解绑基岩版")
    public Result<Boolean> Unbind(@Validated UnbindVO unbindVO) {
        BindType type = BindType.getValueByKey(unbindVO.getType());
        if (type == null) throw ApiException.of(ErrorCode.BIND_TYPE_INVALID);
        switch (type) {
            case JAVA -> premiumService.unbindPremium();
            case BEDROCK -> bedrockService.unbindBedrock();
        }
        return ApiResults.ok(true);
    }
}
