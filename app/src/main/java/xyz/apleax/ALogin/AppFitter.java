package xyz.apleax.ALogin;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.exception.ConstructionException;
import org.noear.solon.core.exception.StatusException;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;
import org.noear.solon.validation.ValidatorException;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;
import xyz.apleax.ALogin.Util.ApiResults;
import xyz.apleax.ALogin.Util.ErrorUtils;

/**
 * 全局异常处理，把各类异常统一渲染成 {code, description, data} 信封。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class AppFitter implements Filter {
    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        try {
            chain.doFilter(ctx);
        } catch (Throwable e) {
            if (ctx.getRendered()) {                       // 已输出，禁止二次渲染
                log.debug("响应已渲染，忽略后续异常: {}", e.toString());
                return;
            }
            ApiException api = ErrorUtils.findApiException(e);
            if (api != null) { renderApi(ctx, api); return; }

            if (ErrorUtils.findCause(e, NotLoginException.class) != null) { renderApi(ctx, ApiException.of(ErrorCode.NOT_LOGIN)); return; }
            if (ErrorUtils.findCause(e, NotPermissionException.class) != null) { renderApi(ctx, ApiException.of(ErrorCode.PERMISSION_DENIED)); return; }
            if (ErrorUtils.findCause(e, NotRoleException.class) != null) { renderApi(ctx, ApiException.of(ErrorCode.GROUP_DENIED)); return; }

            ValidatorException validator = ErrorUtils.findCause(e, ValidatorException.class);
            if (validator != null) { renderApi(ctx, ApiException.raw(ErrorCode.PARAM_INVALID, validator.getMessage())); return; }

            if (e instanceof StatusException se) {          // 协议级状态：保留 HTTP 状态，正文统一编码
                ctx.status(se.getCode());
                renderApi(ctx, ErrorUtils.isJsonMappingError(se) ? ApiException.of(ErrorCode.PARAM_INVALID)
                        : ApiException.raw(ErrorCode.PARAM_INVALID, se.getMessage()));
                return;
            }
            if (e instanceof ConstructionException ce) {
                if (ErrorUtils.isJsonMappingError(ce)) { renderApi(ctx, ApiException.of(ErrorCode.PARAM_INVALID)); return; }
                log.error("服务构造异常", ce);
                renderApi(ctx, ApiException.of(ErrorCode.INTERNAL_ERROR));
                return;
            }

            log.error("未处理异常", e);
            renderApi(ctx, ApiException.of(ErrorCode.INTERNAL_ERROR));
        }
    }

    /**
     * 渲染失败信封；错误码自带非 200 的 HTTP 状态时同步设置状态。
     */
    private void renderApi(Context ctx, ApiException e) throws Throwable {
        ErrorCode code = e.errorCode();
        if (code.httpStatus() != 200) ctx.status(code.httpStatus());
        if (code.code() >= 500000) log.error("请求失败 {} {} code={} message={}", ctx.method(), ctx.path(), code.code(), e.description());
        else log.debug("请求失败 {} {} code={} message={}", ctx.method(), ctx.path(), code.code(), e.description());
        ctx.render(ApiResults.failure(e));
    }
}
