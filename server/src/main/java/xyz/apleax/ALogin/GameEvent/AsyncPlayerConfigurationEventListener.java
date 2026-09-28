package xyz.apleax.ALogin.GameEvent;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.SharedInstance;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.MinestomServerInit;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class AsyncPlayerConfigurationEventListener implements EventListener<@NotNull AsyncPlayerConfigurationEvent> {
    private final InstanceManager instanceManager;
    private final InstanceContainer instanceContainer;

    public AsyncPlayerConfigurationEventListener(InstanceManager instanceManager, InstanceContainer instanceContainer) {
        this.instanceManager = instanceManager;
        this.instanceContainer = instanceContainer;
    }

    @Override
    public @NotNull Class<@NotNull AsyncPlayerConfigurationEvent> eventType() {
        return AsyncPlayerConfigurationEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull AsyncPlayerConfigurationEvent event) {
        final Player player = event.getPlayer();
        SharedInstance sharedInstance = instanceManager.createSharedInstance(instanceContainer);
        MinestomServerInit.playerInstanceMap.put(player.getUuid(), sharedInstance.getUuid());
        event.setSpawningInstance(sharedInstance);
        player.setRespawnPoint(new Pos(0, 42, 0));
        player.setGameMode(GameMode.ADVENTURE);
        return Result.SUCCESS;
    }
}
