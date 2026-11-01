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
import org.noear.solon.core.bean.LifecycleBean;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class PlayerSpawnEventListener implements EventListener<@NotNull PlayerSpawnEvent>, LifecycleBean {
    /**
     * 自动登录时延迟展示兜底链接的时间（正常情况此刻玩家已进游戏）
     */
    private static final long AUTO_LOGIN_LINK_DELAY_MILLIS = 8_000L;
    /**
     * 兜底链接的重复发送间隔（首次可发时立即发，之后按此间隔重复）
     */
    private static final long FALLBACK_LINK_INTERVAL_MILLIS = 10_000L;

    /**
     * 正版自动登录执行器：Dami 同步分发会执行数据库操作，不能在 tick 线程上跑
     */
    private final ExecutorService autoLoginExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final PremiumAssertionService assertionService;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public PlayerSpawnEventListener(PremiumAssertionService assertionService,
                                    @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.assertionService = assertionService;
        this.accountIndexCache = accountIndexCache;
    }

    @Override
    public void stop() {
        autoLoginExecutor.shutdownNow();
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
        String baseUrl = Solon.cfg().get("server.address");
        String link = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "token=" + token;
        TextComponent linkComponent = Component.text().append(
                Component.text("[打开聊天框，点击此处登录]", TextColor.color(195, 87, 219)).clickEvent(ClickEvent.openUrl(link)).appendNewline()
        ).append(
                Component.text("无法打开网页或使用基岩版时可使用指令: /l [邮箱] [密码登录]", TextColor.color(152, 251, 152)).appendNewline()
        ).build();

        AtomicLong linkVisibleAt = new AtomicLong(0L);
        AtomicLong nextLinkAt = new AtomicLong(0L);
        if (assertionService.get(player.getUuid()) != null) {
            linkVisibleAt.set(System.currentTimeMillis() + AUTO_LOGIN_LINK_DELAY_MILLIS);
            autoLoginExecutor.submit(() -> {
                try {
                    if (!requestAutoLogin(player, token)) linkVisibleAt.set(0L);
                } catch (Exception e) {
                    log.warn("正版自动登录触发异常，player={}", player.getUuid(), e);
                    linkVisibleAt.set(0L);
                }
            });
        }

        Scheduler scheduler = player.scheduler();
        // 每秒看一眼即可：自动登录一旦失败（linkVisibleAt 归零）立即给出兜底链接，
        // 而不是干等到下一个 10 秒周期；正常发送仍按 10 秒一次，避免刷屏
        Task tips = scheduler.submitTask(() -> {
            // 玩家已经进服（transfer 后客户端断开）或已掉线：提示与 5 分钟踢人计时都不再需要，
            // 任务由全局定时器驱动、不随实体销毁自动停止，必须自己收尾
            if (!player.isOnline()) return TaskSchedule.stop();
            long now = System.currentTimeMillis();
            if (now >= linkVisibleAt.get() && now >= nextLinkAt.get()) {
                player.sendMessage(linkComponent);
                nextLinkAt.set(now + FALLBACK_LINK_INTERVAL_MILLIS);
            }
            return TaskSchedule.seconds(1);
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
    private boolean requestAutoLogin(Player player, String token) {
        PremiumAssertion assertion = assertionService.get(player.getUuid());
        if (assertion == null) return false;
        String account = accountIndexCache.get(
                new AccountIndexCache(AccountType.PREMIUM_UUID, assertion.uuid().toString()));
        if (account == null) return false;
        // 复用 spawn 阶段生成的进服 token（core 侧不再另建一个），LoginEvent 靠它定位玩家
        Dami.bus().send("PremiumAutoLogin", Map.of(
                "account", account,
                "token", token,
                "premiumUuid", assertion.uuid().toString(),
                "premiumName", assertion.name() == null ? "" : assertion.name(),
                "ip", assertion.ip() == null ? "" : assertion.ip()));
        log.debug("已触发正版自动登录，account={}, premiumUuid={}", account, assertion.uuid());
        return true;
    }
}
