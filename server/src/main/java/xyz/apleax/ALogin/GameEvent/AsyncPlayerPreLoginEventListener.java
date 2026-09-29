package xyz.apleax.ALogin.GameEvent;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerSocketConnection;
import net.minestom.server.network.plugin.LoginPlugin;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class AsyncPlayerPreLoginEventListener implements EventListener<@NotNull AsyncPlayerPreLoginEvent> {
    private final PremiumAssertionService assertionService;

    public AsyncPlayerPreLoginEventListener(PremiumAssertionService assertionService) {
        this.assertionService = assertionService;
    }

    @Override
    public @NotNull Class<AsyncPlayerPreLoginEvent> eventType() {
        return AsyncPlayerPreLoginEvent.class;
    }

    @Override
    public @NotNull Result run(AsyncPlayerPreLoginEvent event) {
        String name = event.getGameProfile().name();
        UUID sessionUuid = UUID.randomUUID();
        event.setGameProfile(new GameProfile(sessionUuid, name));
        requestPremiumAssertion(event, sessionUuid, name);
        return Result.SUCCESS;
    }

    /**
     * 向 ViaProxy 插件索要正版断言：无插件/未应答/校验失败一律按"无断言"处理，不影响登录
     */
    private void requestPremiumAssertion(AsyncPlayerPreLoginEvent event, UUID sessionUuid, String name) {
        if (!assertionService.isEnabled() || !(event.getConnection() instanceof PlayerSocketConnection)) return;
        String challenge = assertionService.createChallenge();
        CompletableFuture<LoginPlugin.Response> future = event.sendPluginRequest(
                assertionService.channel(), challenge.getBytes(StandardCharsets.UTF_8));
        // 自行兜底：登录插件消息无人应答时，Minestom 的 awaitReplies(5s) 超时会踢人
        future.completeOnTimeout(null, assertionService.timeoutMs(), TimeUnit.MILLISECONDS);
        log.debug("已下发正版断言挑战，player={}", name);
        future.thenAccept(response -> {
            try {
                byte[] payload = response == null ? null : response.payload();
                log.debug("正版断言应答，player={}, 载荷={}", name, payload == null ? "无" : payload.length + " 字节");
                PremiumAssertion assertion = assertionService.verify(challenge, name, payload);
                if (assertion != null) assertionService.stash(sessionUuid, assertion);
            } catch (Exception e) {
                log.warn("正版断言处理异常：{}", e.getMessage());
            }
        });
    }
}
