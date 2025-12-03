package xyz.apleax.ALogin;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventListener;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Destroy;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;

import java.net.InetSocketAddress;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class MinestomServerInit {
    @Inject("${minestom.port:25565}")
    private static Integer port;

    @Init
    public void init() {
        MinecraftServer server = MinecraftServer.init();
        server.start(new InetSocketAddress(port));
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        Solon.context().wrapAndPut(InstanceManager.class, instanceManager);
        Solon.context().wrapAndPut(InstanceContainer.class, instanceManager.createInstanceContainer(DimensionType.THE_END));
        Solon.context().getBeansOfType(Command.class).forEach(command -> MinecraftServer.getCommandManager().register(command));
        Solon.context().getBeansOfType(EventListener.class).forEach(eventListener -> {
            EventListener<? extends @NotNull Event> listener = (EventListener<? extends @NotNull Event>) eventListener;
            MinecraftServer.getGlobalEventHandler().addListener(listener);
        });
        log.info("MinestomServer started on port {}", port);
    }

    @Destroy
    public void destroy() {
        MinecraftServer.stopCleanly();
    }
}
