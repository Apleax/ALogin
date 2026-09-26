package xyz.apleax.ALogin.Velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import xyz.apleax.ALogin.Identity.ExternalIdentityProvider;
import xyz.apleax.ALogin.Identity.IdentityAssertionCodec;
import xyz.apleax.ALogin.Identity.VerifiedIdentity;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 把 LibreLogin 已确认的 Java 正版身份转换为 ALogin Cookie 断言。
 *
 * <p>本模块不执行 Mojang 握手，也不读取 LibreLogin 私有数据库，只使用其公开 API 与认证事件。</p>
 *
 * @author Apleax
 */
@Plugin(
        id = "alogin-velocity-identity",
        name = "ALogin Velocity Identity",
        version = "0.6.0",
        authors = {"Apleax"},
        dependencies = @Dependency(id = "librelogin")
)
public final class ALoginVelocityIdentityAdapter {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private LibreLoginPlugin<Player, RegisteredServer> libreLogin;
    private Consumer<AuthenticatedEvent<Player, RegisteredServer>> subscription;
    private VelocityIdentitySettings settings;

    @Inject
    public ALoginVelocityIdentityAdapter(
            ProxyServer proxyServer,
            Logger logger,
            @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        settings = VelocityIdentitySettings.load(dataDirectory, logger);
        if (!settings.enabled()) {
            logger.info("ALogin Java identity adapter is disabled");
            return;
        }

        var container = proxyServer.getPluginManager().getPlugin("librelogin");
        if (container.isEmpty()) {
            logger.error("LibreLogin is required but was not found; Java identity adapter is disabled");
            return;
        }
        Object instance = container.flatMap(PluginContainer::getInstance).orElse(null);
        if (!(instance instanceof LibreLoginProvider<?, ?> provider)) {
            logger.error("The installed LibreLogin does not expose its public provider API");
            return;
        }
        @SuppressWarnings("unchecked")
        LibreLoginProvider<Player, RegisteredServer> typedProvider =
                (LibreLoginProvider<Player, RegisteredServer>) provider;
        libreLogin = typedProvider.getLibreLogin();
        subscription = libreLogin.getEventProvider().subscribe(
                libreLogin.getEventTypes().authenticated,
                this::onAuthenticated);
        logger.info("ALogin Java identity adapter enabled");
    }

    private void onAuthenticated(AuthenticatedEvent<Player, RegisteredServer> event) {
        if (event.getReason() != AuthenticatedEvent.AuthenticationReason.PREMIUM) return;
        Player player = event.getPlayer();
        User user = event.getUser();
        if (player == null || user == null || !player.isOnlineMode()) return;
        UUID premiumUuid = user.getPremiumUUID();
        if (premiumUuid == null) {
            logger.warn("LibreLogin reported a premium authentication without a premium UUID for {}",
                    player.getUsername());
            return;
        }

        try {
            String assertion = IdentityAssertionCodec.issue(
                    new VerifiedIdentity(
                            ExternalIdentityProvider.JAVA_MOJANG,
                            premiumUuid.toString().toLowerCase(Locale.ROOT),
                            player.getUsername(),
                            settings.issuer()),
                    settings.issuer(),
                    settings.audience(),
                    settings.javaSharedSecret(),
                    Instant.now(),
                    Duration.ofMillis(settings.assertionTtlMillis()),
                    null);
            player.storeCookie(Key.key(settings.cookieKey()), assertion.getBytes(StandardCharsets.US_ASCII));
            logger.debug("Stored a Java identity assertion for {}", player.getUsername());
        } catch (RuntimeException exception) {
            logger.warn("Failed to store a Java identity assertion for {}", player.getUsername(), exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (libreLogin != null && subscription != null) {
            libreLogin.getEventProvider().unsubscribe(subscription);
        }
    }
}
