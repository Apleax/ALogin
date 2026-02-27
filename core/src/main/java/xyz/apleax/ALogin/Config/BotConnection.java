package xyz.apleax.ALogin.Config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.noear.java_websocket.client.SimpleWebSocketClient;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.core.serialize.Serializer;
import org.noear.solon.serialization.jackson.JacksonStringSerializer;

import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public class BotConnection implements LifecycleBean {
    private final Serializer<String> serializer = Solon.context().getBean(JacksonStringSerializer.class);
    private SimpleWebSocketClient client;

    @Override
    public void start() {
        log.info("BotConnection Loading Complete");
    }

    @Override
    public void stop() {
        client.release();
    }

    @Managed(name = "BotConnection", index = -100)
    public SimpleWebSocketClient BotWsConnection() throws InterruptedException {
        client = new SimpleWebSocketClient("ws://apleax.xyz:3001") {
            @Override
            @SneakyThrows
            public void onMessage(String message) {
            }
        };
        client.addHeader("Authorization", "Bearer Apleax133137");
        //开始连接
        client.connectBlocking(10, TimeUnit.SECONDS);
        //开始心跳 + 心跳时自动重连
        client.heartbeat(60_000, true);
        return client;
    }
}
