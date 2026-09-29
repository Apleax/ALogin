package xyz.apleax.alogin.viaproxy;

import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.ProxySessionCreationEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.util.logging.Logger;

/**
 * ALogin 正版免密断言插件：对已绑定正版的连接做混合校验并回传 HMAC 断言
 *
 * @author Apleax
 */
public class ALoginPremiumPlugin extends ViaProxyPlugin {

    private PluginConfig config;
    private BoundPremiumList boundPremiumList;

    /**
     * 启用插件：加载配置、启动名单刷新，断言处理器需插到会话处理器链首
     */
    @Override
    public void onEnable() {
        this.config = PluginConfig.load(this.getDataFolder());
        if (!this.config.enabled() || this.config.secret().isBlank()) {
            Logger.LOGGER.info("[ALoginPremium] 未启用（enabled={}，secret={}），所有连接按普通流程处理",
                    this.config.enabled(), this.config.secret().isBlank() ? "未配置" : "已配置");
            return;
        }
        if (!this.config.fakePremiumUuid().isBlank()) {
            Logger.LOGGER.warn("[ALoginPremium] !!! 测试模式开启（fake-premium-uuid={}），跳过正版校验，请勿在生产使用 !!!",
                    this.config.fakePremiumUuid());
        } else if (this.config.aloginUrl().isBlank()) {
            Logger.LOGGER.error("[ALoginPremium] 未配置 alogin-url（如 http://127.0.0.1:8080），"
                    + "无法获取已绑定正版名单，正版免密校验不启用，所有连接按普通流程处理");
            return;
        } else {
            this.boundPremiumList = new BoundPremiumList(this.config);
            this.boundPremiumList.start();
        }

        ViaProxy.EVENT_MANAGER.registerConsumer((final ProxySessionCreationEvent<?> event) -> {
                    if (event.isLegacyPassthrough()) return;
                    if (event.getProxySession() instanceof ProxyConnection session) {
                        session.getPacketHandlers().add(0, new PremiumAssertionHandler(session, this.config, this.boundPremiumList));
                    }
                },
                ProxySessionCreationEvent.class);

        Logger.LOGGER.info("[ALoginPremium] 已启用：channel={}, timeout={}ms, alogin-url={}",
                this.config.channel(), this.config.verifyTimeoutMs(), this.config.aloginUrl());
    }

    @Override
    public void onDisable() {
        if (this.boundPremiumList != null) this.boundPremiumList.stop();
        Logger.LOGGER.info("[ALoginPremium] 已停用");
    }
}
