package xyz.apleax.ALogin.GameEvent;

import cn.dev33.satoken.temp.SaTempUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Managed;

import java.time.Duration;

/**
 *
 *
 * @author Apleax
 */
@Managed(index = -999)
public class PlayerSpawnEventListener implements EventListener<@NotNull PlayerSpawnEvent> {
    @Override
    public @NotNull Class<@NotNull PlayerSpawnEvent> eventType() {
        return PlayerSpawnEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        event.getPlayer().setVelocity(Vec.ZERO);
        player.setFlying(true);
        String token = SaTempUtil.createToken(player.getUuid(), Duration.ofMinutes(5).getSeconds());
        String link = Solon.cfg().get("server.address");
        if (!link.endsWith("/")) link += "/";
        TextComponent linkComponent = Component.text("[登录]", TextColor.color(152, 251, 152))
                .clickEvent(ClickEvent.openUrl(link + token));
        Scheduler scheduler = player.scheduler();
        scheduler.submitTask(() -> {
            player.sendMessage(linkComponent);
            return TaskSchedule.seconds(10);
        });
        return Result.SUCCESS;
    }
}
