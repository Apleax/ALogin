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

    /**
     * 为玩家创建共享实例；先登记再复核在线状态，登记后断开由 PlayerDisconnectEvent 清理，
     * 登记前断开由本方法自行回收（防止创建期间断线导致实例泄漏）
     */
    @Override
    public @NotNull Result run(@NotNull AsyncPlayerConfigurationEvent event) {
        final Player player = event.getPlayer();
        if (!player.isOnline()) return Result.SUCCESS;
        SharedInstance sharedInstance = instanceManager.createSharedInstance(instanceContainer);
        MinestomServerInit.playerInstanceMap.put(player.getUuid(), sharedInstance.getUuid());
        if (!player.isOnline()) {
            if (MinestomServerInit.playerInstanceMap.remove(player.getUuid()) != null)
                instanceManager.unregisterInstance(sharedInstance);
            return Result.SUCCESS;
        }
        event.setSpawningInstance(sharedInstance);
        player.setRespawnPoint(new Pos(0, 42, 0));
        player.setGameMode(GameMode.ADVENTURE);
        return Result.SUCCESS;
    }
}
