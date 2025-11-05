package xyz.apleax.ALogin.Config;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;

import java.net.InetSocketAddress;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public class MinestomServerInit {
    @Inject("${minestom.port:25565}")
    private static Integer port;

    @Managed(index = -100, name = "MinestomServer")
    public MinecraftServer init() {
        MinecraftServer server = MinecraftServer.init();
        server.start(new InetSocketAddress(port));
        log.info("MinestomServer started on port {}", port);
        return server;
    }
}
