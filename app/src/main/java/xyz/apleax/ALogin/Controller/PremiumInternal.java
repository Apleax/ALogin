package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.AllArgsConstructor;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Valid;
import xyz.apleax.ALogin.Service.PremiumService;

/**
 * 正版相关的内部接口（供 ViaProxy 插件调用，密钥即 Premium.assertion.secret）
 *
 * @author Apleax
 */
@Valid
@Controller
@AllArgsConstructor
@Mapping(path = "/api/internal/premium")
public class PremiumInternal {
    /**
     * 代理插件查询名单时携带的请求头名（值 = Premium.assertion.secret）
     */
    private static final String KEY_HEADER = "X-ALogin-Key";

    private final PremiumService premiumService;

    @SaIgnore
    @Mapping(path = "/BoundUuids", method = MethodType.GET,
            name = "已绑定正版名单", description = "返回已绑定正版 UUID 列表（换行分隔），供代理插件过滤需要发起会话校验的连接")
    public Result<String> BoundUuids(Context context) {
        Result<String> result = premiumService.getBoundUuids(context.header(KEY_HEADER));
        if (result.getCode() != Result.SUCCEED_CODE) context.status(403);
        return result;
    }
}
