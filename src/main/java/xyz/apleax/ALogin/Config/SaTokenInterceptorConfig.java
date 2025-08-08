package xyz.apleax.ALogin.Config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.solon.integration.SaTokenInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/**
 * Sa-Token 路由过滤器配置
 */
@Configuration
public class SaTokenInterceptorConfig {
    @Bean(index = -100)  //-100，是顺序位（低值优先）
    public SaTokenInterceptor saTokenInterceptor() {
        return new SaTokenInterceptor()
                // 指定 [拦截路由] 与 [放行路由]
                .addInclude("/**").addExclude("/account/Register", "/account/LoginByEmail",
                        "/account/LoginByAccount", "/account/RegisterVerifyCode")

                // 认证函数: 每次请求执行
                .setAuth(req -> SaRouter.match("/**", StpUtil::checkLogin))
                // 前置函数：在每次认证函数之前执行
                .setBeforeAuth(req -> {
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
}