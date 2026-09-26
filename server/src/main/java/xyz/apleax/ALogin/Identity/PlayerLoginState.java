package xyz.apleax.ALogin.Identity;

import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Identity.VerifiedIdentity;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录服内的玩家会话辅助状态。
 *
 * <p>它只用于把 Minestom 玩家连接与 ALogin 账号、最近一次已验证外部身份关联起来；
 * 持久化绑定仍然只在数据库中保存。</p>
 *
 * @author Apleax
 */
@Managed
public class PlayerLoginState {
    private static final long IDENTITY_CONTEXT_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

    private final Map<UUID, String> loggedInAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, IdentityContext> identities = new ConcurrentHashMap<>();

    public void markLoggedIn(UUID playerId, String account) {
        if (playerId != null && account != null && !account.isBlank()) loggedInAccounts.put(playerId, account);
    }

    public String accountOf(UUID playerId) {
        return loggedInAccounts.get(playerId);
    }

    public void rememberIdentity(UUID playerId, VerifiedIdentity identity) {
        if (playerId != null && identity != null) {
            identities.put(playerId, new IdentityContext(
                    identity, System.currentTimeMillis() + IDENTITY_CONTEXT_TTL_MILLIS));
        }
    }

    public VerifiedIdentity identityOf(UUID playerId) {
        IdentityContext context = identities.get(playerId);
        if (context == null) return null;
        if (context.expiresAt() < System.currentTimeMillis()) {
            identities.remove(playerId, context);
            return null;
        }
        return context.identity();
    }

    public void remove(UUID playerId) {
        loggedInAccounts.remove(playerId);
        identities.remove(playerId);
    }

    private record IdentityContext(VerifiedIdentity identity, long expiresAt) {
    }
}
