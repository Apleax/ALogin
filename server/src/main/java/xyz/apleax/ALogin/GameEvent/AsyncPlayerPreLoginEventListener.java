package xyz.apleax.ALogin.GameEvent;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.network.player.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

import java.util.UUID;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class AsyncPlayerPreLoginEventListener implements EventListener<@NotNull AsyncPlayerPreLoginEvent> {

    @Override
    public @NotNull Class<AsyncPlayerPreLoginEvent> eventType() {
        return AsyncPlayerPreLoginEvent.class;
    }

    @Override
    public @NotNull Result run(AsyncPlayerPreLoginEvent event) {
        event.setGameProfile(new GameProfile(UUID.randomUUID(), event.getGameProfile().name()));
        return Result.SUCCESS;
    }
}
