package xyz.apleax.ALogin.Geyser;

import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.jspecify.annotations.NonNull;

/**
 * 只替换地址与端口的下游服务器描述，其余配置沿用原下游
 *
 * @author Apleax
 */
public record RedirectedRemoteServer(RemoteServer original, String host, int port) implements RemoteServer {
    @Override
    public @NonNull String address() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public int protocolVersion() {
        return original.protocolVersion();
    }

    @Override
    public @NonNull String minecraftVersion() {
        return original.minecraftVersion();
    }

    @Override
    public @NonNull AuthType authType() {
        return original.authType();
    }

    @Override
    public boolean resolveSrv() {
        return original.resolveSrv();
    }
}
