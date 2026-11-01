package xyz.apleax.ALogin.viaproxy;

import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.ProxySessionCreationEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.util.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ALogin 正版免密断言插件：对已绑定正版的连接做混合校验并回传 HMAC 断言
 *
 * @author Apleax
 */
public class ALoginPremiumPlugin extends ViaProxyPlugin {

    private xyz.apleax.ALogin.viaproxy.PluginConfig config;
    private xyz.apleax.ALogin.viaproxy.BoundPremiumList boundPremiumList;
    private ExecutorService authExecutor;
    /**
     * 停用后置 false，让已注册的会话创建监听器失效（ViaProxy 的 LambdaManager 不支持注销单个消费者）
     */
    private final AtomicBoolean active = new AtomicBoolean();

    /**
     * 启用插件：加载配置、启动名单刷新，断言处理器需插到会话处理器链首
     */
    @Override
    public void onEnable() {
        this.config = xyz.apleax.ALogin.viaproxy.PluginConfig.load(this.getDataFolder());
        if (!this.config.enabled() || this.config.secret().isBlank()) {
            Logger.LOGGER.info("[ALoginPremium] 未启用（enabled={}，secret={}），所有连接按普通流程处理",
                    this.config.enabled(), this.config.secret().isBlank() ? "未配置" : "已配置");
            return;
        }
        if (!this.config.fakePremiumUuid().isBlank() && !this.config.fakePremiumAllowInProd()) {
            Logger.LOGGER.error("[ALoginPremium] 已设置 fake-premium-uuid 但未开启 fake-premium-allow-in-prod，"
                    + "拒绝启用（防止测试模式误上线）");
            return;
        }
        if (!this.config.fakePremiumUuid().isBlank())
            Logger.LOGGER.warn("[ALoginPremium] !!! 测试模式开启（fake-premium-uuid={}），跳过正版校验，请勿在生产使用 !!!",
                    this.config.fakePremiumUuid());
        else if (this.config.aloginUrl().isBlank()) {
            Logger.LOGGER.error("[ALoginPremium] 未配置 alogin-url（如 http://127.0.0.1:8080），"
                    + "无法获取已绑定正版名单，正版免密校验不启用，所有连接按普通流程处理");
            return;
        } else {
            this.boundPremiumList = new xyz.apleax.ALogin.viaproxy.BoundPremiumList(this.config);
            this.boundPremiumList.start();
        }

        this.authExecutor = Executors.newFixedThreadPool(8, r -> {
            final Thread thread = new Thread(r, "ALoginPremium-Auth");
            thread.setDaemon(true);
            return thread;
        });
        this.active.set(true);
        ViaProxy.EVENT_MANAGER.registerConsumer((final ProxySessionCreationEvent<?> event) -> {
                    if (!active.get()) return;
                    if (event.isLegacyPassthrough()) return;
                    if (event.getProxySession() instanceof ProxyConnection session) session.getPacketHandlers().add(0,
                            new PremiumAssertionHandler(session, this.config, this.boundPremiumList, this.authExecutor));
                },
                ProxySessionCreationEvent.class);

        Logger.LOGGER.info("[ALoginPremium] 已启用：channel={}, timeout={}ms, alogin-url={}",
                this.config.channel(), this.config.verifyTimeoutMs(), stripUserInfo(this.config.aloginUrl()));
    }

    @Override
    public void onDisable() {
        this.active.set(false);
        if (this.boundPremiumList != null) this.boundPremiumList.stop();
        if (this.authExecutor != null) this.authExecutor.shutdownNow();
        Logger.LOGGER.info("[ALoginPremium] 已停用");
    }

    /**
     * 日志输出时隐藏 URL 中的内嵌凭据
     */
    private static String stripUserInfo(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        if (scheme < 0) return url;
        int at = url.indexOf('@', scheme + 3);
        int slash = url.indexOf('/', scheme + 3);
        if (at >= 0 && (slash < 0 || at < slash)) return url.substring(0, scheme + 3) + "***" + url.substring(at);
        return url;
    }
}
