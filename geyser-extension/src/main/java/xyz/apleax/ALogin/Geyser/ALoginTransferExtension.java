package xyz.apleax.ALogin.Geyser;

import org.geysermc.event.PostOrder;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.java.ServerTransferEvent;
import org.geysermc.geyser.api.extension.Extension;
import xyz.apleax.ALogin.Identity.ExternalIdentityProvider;
import xyz.apleax.ALogin.Identity.IdentityAssertionCodec;
import xyz.apleax.ALogin.Identity.VerifiedIdentity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 让基岩版玩家走“登录服 -> 代理”的转移流程，并把 Floodgate XUID 作为受信任身份断言传给 ALogin。
 *
 * <p>身份断言默认关闭。启用时，断言由 Geyser 扩展使用共享 HMAC 密钥签发，
 * ALogin 只接受签名、受众、有效期和一次性 jti 都正确的断言。</p>
 *
 * @author Apleax
 */
public class ALoginTransferExtension implements Extension {
    private final Map<String, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();
    private volatile Settings settings;

    @Subscribe(postOrder = PostOrder.FIRST)
    public void onServerTransfer(ServerTransferEvent event) {
        Settings current = settings();
        GeyserConnection connection = event.connection();
        String xuid = connection.xuid();
        if (xuid.isBlank()) {
            logger().error("Missing the XUID of " + connection.bedrockUsername() + ", ignored the transfer");
            return;
        }
        if (current.transferTarget() != null && !current.transferTarget().matches(event.host(), event.port())) {
            logger().debug("Ignored the transfer of " + connection.bedrockUsername()
                    + " to " + event.host() + ":" + event.port());
            return;
        }

        Settings.HostPort bedrock = current.bedrockAddress() != null
                ? current.bedrockAddress() : joinAddressOf(connection);
        if (bedrock == null) {
            logger().error("Unable to resolve the bedrock address of " + connection.bedrockUsername()
                    + ", please set 'bedrock-address' in " + Settings.FILE_NAME);
            return;
        }

        remember(xuid, new PendingTransfer(event.cookies(), event.host(), event.port(),
                System.currentTimeMillis() + current.cookieTtlMillis()));
        event.bedrockHost(bedrock.host());
        event.bedrockPort(bedrock.port());
        logger().info("Moving " + connection.bedrockUsername() + " back to " + bedrock
                + " to hand it over to " + event.host() + ":" + event.port());
    }

    @Subscribe(postOrder = PostOrder.FIRST)
    public void onSessionLogin(SessionLoginEvent event) {
        Settings current = settings();
        GeyserConnection connection = event.connection();
        String xuid = connection.xuid();
        PendingTransfer pending = xuid.isBlank() ? null : pendingTransfers.remove(xuid);
        if (pending == null) {
            issueIdentityCookie(current, connection, event.cookies());
            return;
        }
        if (pending.isExpired()) {
            logger().warning("Dropped the expired transfer of " + connection.bedrockUsername());
            return;
        }

        String targetHost = current.bedrockTarget() != null ? current.bedrockTarget().host() : pending.host();
        int targetPort = current.bedrockTarget() != null ? current.bedrockTarget().port() : pending.port();
        Map<String, byte[]> cookies = pending.cookies();
        if (cookies.isEmpty()) logger().warning("The transfer of " + connection.bedrockUsername()
                + " carried no cookies yet, " + targetHost + ":" + targetPort
                + " will get no token if the table stays empty");
        issueIdentityCookie(current, connection, cookies);
        event.cookies(cookies);
        event.remoteServer(new RedirectedRemoteServer(event.remoteServer(), targetHost, targetPort));
        event.transferring(true);

        logger().info("Handing " + connection.bedrockUsername() + " over to " + targetHost + ":" + targetPort
                + " with " + cookies.size() + " cookie(s)");
    }

    private void issueIdentityCookie(Settings current, GeyserConnection connection, Map<String, byte[]> cookies) {
        if (!current.identityEnabled() || current.identitySharedSecret().isBlank()
                || connection.xuid().isBlank()) return;
        try {
            String assertion = IdentityAssertionCodec.issue(
                    new VerifiedIdentity(
                            ExternalIdentityProvider.BEDROCK_XUID,
                            connection.xuid(),
                            connection.bedrockUsername(),
                            current.identityIssuer()),
                    current.identityIssuer(),
                    current.identityAudience(),
                    current.identitySharedSecret(),
                    Instant.now(),
                    Duration.ofMillis(current.identityAssertionTtlMillis()),
                    null);
            cookies.put(current.identityCookieKey(), assertion.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException exception) {
            logger().error("Failed to issue the Bedrock identity assertion", exception);
        }
    }

    private void remember(String xuid, PendingTransfer transfer) {
        pendingTransfers.values().removeIf(PendingTransfer::isExpired);
        pendingTransfers.put(xuid, transfer);
    }

    private Settings.HostPort joinAddressOf(GeyserConnection connection) {
        try {
            return new Settings.HostPort(connection.joinAddress(), connection.joinPort());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Settings settings() {
        Settings current = settings;
        if (current != null) return current;
        synchronized (this) {
            if (settings == null) settings = Settings.load(dataFolder(), logger());
            return settings;
        }
    }

    private record PendingTransfer(Map<String, byte[]> cookies, String host, int port, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
