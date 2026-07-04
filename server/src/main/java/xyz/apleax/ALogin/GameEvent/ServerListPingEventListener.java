package xyz.apleax.ALogin.GameEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.ping.ServerListPingType;
import net.minestom.server.ping.Status;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class ServerListPingEventListener implements EventListener<@NotNull ServerListPingEvent> {

    @Override
    public @NotNull Class<ServerListPingEvent> eventType() {
        return ServerListPingEvent.class;
    }

    @Override
    public @NotNull Result run(ServerListPingEvent event) {
        //            byte[] favicon;
        if (event.getPingType().equals(ServerListPingType.MODERN_FULL_RGB)) event.setStatus(Status.builder()
                .description(Component.text("ApServer", TextColor.color(9, 173, 211)))
                // .favicon(favicon)
                .playerInfo(10, 400)
                .versionInfo(new Status.VersionInfo("26.1.2", 775))
                .build());
        return Result.SUCCESS;
    }
}
