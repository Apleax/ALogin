package xyz.apleax.ALogin.Config;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.bean.LifecycleBean;

/**
 * 外部机器人 WebSocket 连接
 * <p>
 * TODO 功能暂未启用：连接已整体注释，待外部系统对接后恢复；
 * 恢复时需重新引入 java-websocket-ns 依赖的 Bean 方法并在 start/stop 中管理连接生命周期。
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public class BotConnection implements LifecycleBean {
    // ===== TODO Bot 功能暂未启用，恢复时取消以下注释 =====
    // private final SimpleWebSocketClient client;
    // private final Serializer<String> serializer =
    //         Solon.context().getBean(JacksonStringSerializer.class);
    //
    // @Managed(name = "BotConnection", index = -100)
    // public SimpleWebSocketClient BotWsConnection() {
    //     client = new SimpleWebSocketClient("ws://apleax.xyz:3001") {
    //         @Override
    //         @SneakyThrows
    //         public void onMessage(String message) {
    //             log.info("BotConnection Received: {}", message);
    //         }
    //     };
    //     client.addHeader("Authorization", "Bearer ");
    //     client.connect();
    //     client.heartbeat(60_000, true);
    //     return client;
    // }
    //
    // @Override
    // public void start() {
    //     log.info("BotConnection Loading Complete");
    // }
    //
    // @Override
    // public void stop() {
    //     if (client != null) client.release();
    // }
}
