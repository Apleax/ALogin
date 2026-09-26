package xyz.apleax.ALogin.Identity;

import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从受信任入口传入的 Cookie 取出短期身份断言，并在命中绑定时自动登录。
 *
 * @author Apleax
 */
@Slf4j
@Managed(index = -998)
@Condition(onClass = MinecraftServer.class)
public class IdentityCookieLoginEventListener implements EventListener<@NotNull PlayerSpawnEvent> {
    private final IdentityLoginService identityLoginService;
    private final IdentityConfig identityConfig;
    private final PlayerLoginState loginState;
    private final LoginTransferService transferService;
    private final IdentityMessages messages;
    private final Set<UUID> attemptedPlayers = ConcurrentHashMap.newKeySet();

    public IdentityCookieLoginEventListener(
            IdentityLoginService identityLoginService,
            IdentityConfig identityConfig,
            PlayerLoginState loginState,
            LoginTransferService transferService,
            IdentityMessages messages) {
        this.identityLoginService = identityLoginService;
        this.identityConfig = identityConfig;
        this.loginState = loginState;
        this.transferService = transferService;
        this.messages = messages;
    }

    @Override
    public @NotNull Class<PlayerSpawnEvent> eventType() {
        return PlayerSpawnEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (!identityConfig.enabled() || !attemptedPlayers.add(player.getUuid())) return Result.SUCCESS;

        player.getPlayerConnection().fetchCookie(identityConfig.cookieKey())
                .thenAcceptAsync(cookie -> handleCookie(player, cookie))
                .exceptionally(exception -> {
                    Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                            ? exception.getCause() : exception;
                    log.debug("Failed to fetch identity cookie for {}: {}", player.getUuid(), cause.getMessage());
                    return null;
                });
        return Result.SUCCESS;
    }

    public void forget(UUID playerId) {
        if (playerId != null) attemptedPlayers.remove(playerId);
    }

    private void handleCookie(Player player, byte[] cookie) {
        if (cookie == null || cookie.length == 0) return;
        try {
            String token = new String(cookie, StandardCharsets.US_ASCII);
            IdentityLoginService.IdentityResolution resolution = identityLoginService.resolve(token);
            loginState.rememberIdentity(player.getUuid(), resolution.assertion().identity());
            if (!resolution.isBound()) {
                schedule(player, () -> player.sendMessage(messages.text(
                        "identity.automatic.unbound",
                        "已验证外部身份，但尚未绑定 ALogin 账号；请先密码登录后执行 /identity bind")));
                return;
            }
            String remoteIp = String.valueOf(player.getPlayerConnection().getRemoteAddress());
            String account = resolution.account().getAccount();
            identityLoginService.login(resolution, remoteIp);
            schedule(player, () -> {
                if (isOnline(player)) {
                    loginState.markLoggedIn(player.getUuid(), account);
                    transferService.transfer(player, account);
                }
            });
        } catch (IdentityBindingException | IllegalArgumentException exception) {
            log.debug("Rejected external identity for {}: {}", player.getUuid(), exception.getMessage());
            schedule(player, () -> player.sendMessage(messages.text(
                    "identity.automatic.invalid", "外部身份验证失败，请使用密码登录")));
        } catch (RuntimeException exception) {
            log.warn("External identity login failed for {}", player.getUuid(), exception);
            schedule(player, () -> player.sendMessage(messages.text(
                    "identity.automatic.unavailable", "外部身份登录暂时不可用，请使用密码登录")));
        }
    }

    private static boolean isOnline(Player player) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(player.getUuid()) != null;
    }

    private static void schedule(Player player, Runnable action) {
        player.scheduler().scheduleTask(() -> {
            action.run();
            return net.minestom.server.timer.TaskSchedule.stop();
        }, net.minestom.server.timer.TaskSchedule.nextTick());
    }
}
