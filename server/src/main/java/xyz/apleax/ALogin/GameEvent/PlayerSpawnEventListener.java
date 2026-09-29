package xyz.apleax.ALogin.GameEvent;

import cn.dev33.satoken.temp.SaTempUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.Dami;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;

import java.time.Duration;
import java.util.Map;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class PlayerSpawnEventListener implements EventListener<@NotNull PlayerSpawnEvent> {
    /**
     * 自动登录时延迟展示兜底链接的时间（正常情况此刻玩家已进游戏）
     */
    private static final long AUTO_LOGIN_LINK_DELAY_MILLIS = 8_000L;

    private final PremiumAssertionService assertionService;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public PlayerSpawnEventListener(PremiumAssertionService assertionService,
                                    @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.assertionService = assertionService;
        this.accountIndexCache = accountIndexCache;
    }

    @Override
    public @NotNull Class<@NotNull PlayerSpawnEvent> eventType() {
        return PlayerSpawnEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        event.getPlayer().setVelocity(Vec.ZERO);
        player.setFlying(true);
        String token = SaTempUtil.createToken(player.getUuid(), Duration.ofMinutes(5).getSeconds(), true);
        String link = Solon.cfg().get("server.address");
        if (!link.endsWith("/")) link += "/";
        TextComponent linkComponent = Component.text().append(
                Component.text("[打开聊天框，点击此处登录]", TextColor.color(195, 87, 219)).clickEvent(ClickEvent.openUrl(link + token)).appendNewline()
        ).append(
                Component.text("无法打开网页或使用基岩版时可使用指令: /l [邮箱] [密码登录]", TextColor.color(152, 251, 152)).appendNewline()
        ).build();

        boolean autoLogin = requestAutoLogin(player);
        if (autoLogin)
            player.sendMessage(Component.text("已识别正版账号，正在自动进入服务器…", TextColor.color(152, 251, 152)));
        // 自动登录时延迟展示兜底链接：正常情况此刻玩家已进游戏
        final long linkVisibleAt = autoLogin ? System.currentTimeMillis() + AUTO_LOGIN_LINK_DELAY_MILLIS : 0L;

        Scheduler scheduler = player.scheduler();
        Task tips = scheduler.submitTask(() -> {
            if (System.currentTimeMillis() >= linkVisibleAt) player.sendMessage(linkComponent);
            return TaskSchedule.seconds(10);
        });
        scheduler.scheduleTask(() -> {
            player.kick(Component.text("您已超过5分钟未登录"));
            tips.cancel();
            return TaskSchedule.stop();
        }, TaskSchedule.duration(Duration.ofMinutes(5)));
        return Result.SUCCESS;
    }

    /**
     * 正版自动登录：本次会话有正版断言且已绑定账号时，通知 core 完成登录并放行
     */
    private boolean requestAutoLogin(Player player) {
        PremiumAssertion assertion = assertionService.get(player.getUuid());
        if (assertion == null) return false;
        String account = accountIndexCache.get(
                new AccountIndexCache(AccountType.PREMIUM_UUID, assertion.uuid().toString()));
        if (account == null) return false;
        Dami.bus().send("PremiumAutoLogin", Map.of(
                "account", account,
                "playerUuid", player.getUuid().toString(),
                "premiumUuid", assertion.uuid().toString(),
                "premiumName", assertion.name() == null ? "" : assertion.name(),
                "ip", assertion.ip() == null ? "" : assertion.ip()));
        log.info("已触发正版自动登录，account={}, premiumUuid={}", account, assertion.uuid());
        return true;
    }
}
