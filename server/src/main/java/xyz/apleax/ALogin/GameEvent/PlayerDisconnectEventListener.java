package xyz.apleax.ALogin.GameEvent;

import cn.dev33.satoken.temp.SaTempUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.InstanceManager;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.MinestomServerInit;

import java.util.Optional;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class PlayerDisconnectEventListener implements EventListener<@NotNull PlayerDisconnectEvent> {
    private final InstanceManager instanceManager;

    public PlayerDisconnectEventListener(InstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    @Override
    public @NotNull Class<@NotNull PlayerDisconnectEvent> eventType() {
        return PlayerDisconnectEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerDisconnectEvent event) {
        Optional.ofNullable(instanceManager.getInstance(
                        MinestomServerInit.playerInstanceMap
                                .get(event.getPlayer().getUuid())))
                .ifPresent(instanceManager::unregisterInstance);
        MinestomServerInit.playerInstanceMap.remove(event.getPlayer().getUuid());
        SaTempUtil.getTempTokenList(event.getPlayer().getUuid()).forEach(
                SaTempUtil::deleteToken
        );
        return Result.SUCCESS;
    }
}
