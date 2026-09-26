package xyz.apleax.ALogin.ALoginEvent;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.noear.dami2.bus.Event;
import org.noear.dami2.bus.EventListener;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Identity.LoginTransferService;
import xyz.apleax.ALogin.Identity.PlayerLoginState;

import java.util.Map;
import java.util.UUID;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("LoginEvent")
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginEventListener implements EventListener<Map<String, String>> {
    private final PlayerLoginState loginState;
    private final LoginTransferService transferService;

    public LoginEventListener(PlayerLoginState loginState, LoginTransferService transferService) {
        this.loginState = loginState;
        this.transferService = transferService;
    }

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        UUID value = SaTempUtil.parseToken(map.get("token"), UUID.class);
        if (value == null) return;
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(value);
        if (player == null) return;
        loginState.markLoggedIn(player.getUuid(), map.get("account"));
        transferService.transfer(player, map.get("account"));
        SaTempUtil.deleteToken(map.get("token"));
    }
}
