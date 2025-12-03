package xyz.apleax.ALogin.GameEvent;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Managed;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
public class PlayerMoveEventListener implements EventListener<@NotNull PlayerMoveEvent> {
    @Override
    public @NotNull Class<PlayerMoveEvent> eventType() {
        return PlayerMoveEvent.class;
    }

    @Override
    public @NotNull Result run(PlayerMoveEvent event) {
        event.setNewPosition(event.getNewPosition().withCoord(0, 42, 0));
        return Result.SUCCESS;
    }
}
