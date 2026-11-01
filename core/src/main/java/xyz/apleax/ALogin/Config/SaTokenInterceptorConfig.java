package xyz.apleax.ALogin.Config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.solon.integration.SaTokenInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import xyz.apleax.ALogin.Service.Permission.PermissionService;

/**
 * Sa-Token 路由过滤器配置
 */
@Slf4j
@Configuration
public record SaTokenInterceptorConfig() implements LifecycleBean {

    @Override
    public void start() {
        log.info("SaToken Loading Complete");
    }

    @Managed(index = -100)  //-100，是顺序位（低值优先）
    public SaTokenInterceptor saTokenInterceptor(PermissionService permissionService) {
        log.info("SaToken Loading...");
        return new SaTokenInterceptor()
                // 指定 [拦截路由]
                .addInclude("/**")
                // 认证函数: 每次请求执行（登录校验 + 可配置的接口权限鉴定）
                .setAuth(_ -> {
                    SaRouter.match("/**", StpUtil::checkLogin);
                    checkApiPermission(permissionService);
                })
                // 前置函数：在每次认证函数之前执行
                .setBeforeAuth(_ -> {
                    // ---------- 设置一些安全响应头 ----------
                    SaHolder.getResponse()
                            // 服务器名称
                            .setServer(Solon.cfg().appName())
                            // 是否可以在iframe显示视图： DENY=不可以 | SAMEORIGIN=同域下可以 | ALLOW-FROM uri=指定域名下可以
                            .setHeader("X-Frame-Options", "DENY")
                            // 是否启用浏览器默认XSS防护： 0=禁用 | 1=启用 | 1; mode=blo1ck 启用, 并在检查到XSS攻击时，停止渲染页面
                            .setHeader("X-XSS-Protection", "1; mode=block")
                            // 禁用浏览器内容嗅探
                            .setHeader("X-Content-Type-Options", "nosniff");
                });
    }

    /**
     * 接口级权限鉴定：命中 permission.yml 中配置的规则时校验权限节点与/或权限组；未命中的接口不做校验
     * <p>
     * 同一请求可能命中多条规则，**全部生效**（逻辑与），避免宽规则把严规则静默覆盖。
     * 鉴定失败（LuckPerms 不可达等）默认 fail-closed，会由 {@link PermissionService} 返回空权限集合而拒绝访问。
     */
    private static void checkApiPermission(PermissionService permissionService) {
        if (!permissionService.enabled()) return;
        SaRequest request = SaHolder.getRequest();
        if (request == null) return;
        for (PermissionProperties.Api.Rule rule : permissionService.matchApiRules(request.getRequestPath(), request.getMethod())) {
            if (rule.permission() != null && !rule.permission().isBlank()) StpUtil.checkPermission(rule.permission());
            // Sa-Token 的权限组校验方法固定叫 checkRole，这里承载的就是"权限组"语义
            if (rule.group() != null && !rule.group().isBlank()) StpUtil.checkRole(rule.group());
        }
    }
}