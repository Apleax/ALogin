package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.AllArgsConstructor;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Valid;
import xyz.apleax.ALogin.POJO.PremiumDeviceCode;
import xyz.apleax.ALogin.POJO.PremiumFlowStatus;
import xyz.apleax.ALogin.Service.PremiumService;

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

    @SaIgnore
    @Mapping(path = "/LoginCode", method = MethodType.GET,
            name = "正版一键进入授权", description = "发起微软设备码授权，返回用户码供玩家在任意设备上输入")
    public Result<PremiumDeviceCode> LoginCode(String token, Context context) {
        if (token == null || token.isBlank()) return Result.failure("缺少进服会话 token");
        return premiumService.getLoginCode(token, context.realIp());
    }

    @Mapping(path = "/BindCode", method = MethodType.GET,
            name = "正版绑定授权", description = "发起微软设备码授权，返回用户码供玩家在任意设备上输入")
    public Result<PremiumDeviceCode> BindCode() {
        return premiumService.getBindCode();
    }

    @SaIgnore
    @Mapping(path = "/Status", method = MethodType.GET,
            name = "正版授权状态", description = "轮询设备码授权结果：pending/success/error")
    public Result<PremiumFlowStatus> Status(String flow) {
        if (flow == null || flow.isBlank()) return Result.failure("缺少授权流程标识");
        return premiumService.getStatus(flow);
    }
}
