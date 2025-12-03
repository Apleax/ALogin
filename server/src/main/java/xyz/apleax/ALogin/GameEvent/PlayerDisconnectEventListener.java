package xyz.apleax.ALogin.GameEvent;

import cn.dev33.satoken.temp.SaTempUtil;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.InstanceManager;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Managed;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
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
        instanceManager.unregisterInstance(event.getPlayer().getInstance());
        SaTempUtil.getTempTokenList(event.getPlayer().getUuid()).forEach(
                SaTempUtil::deleteToken
        );
        return Result.SUCCESS;
    }
}
