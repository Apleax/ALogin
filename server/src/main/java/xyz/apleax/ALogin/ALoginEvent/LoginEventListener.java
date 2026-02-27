package xyz.apleax.ALogin.ALoginEvent;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.TransferPacket;
import org.noear.dami2.bus.Event;
import org.noear.dami2.bus.EventListener;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

import java.nio.charset.StandardCharsets;
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
    private static final String transfer = Solon.cfg().get("minestom.transfer");
    private static final String cookieKey = Solon.cfg().get("minestom.cookie-key");

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        UUID value = SaTempUtil.parseToken(map.get("token"), UUID.class);
        if (value == null) return;
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(value);
        if (player == null) return;
        String address;
        int port;
        if (transfer.contains(":")) {
            address = transfer.split(":")[0];
            port = Integer.parseInt(transfer.split(":")[1]);
        } else {
            address = transfer;
            port = 25565;
        }
        player.getPlayerConnection().storeCookie(cookieKey + ":token", StpUtil.getTokenValueByLoginId(map.get("account")).getBytes(StandardCharsets.UTF_8));
        player.sendPacket(new TransferPacket(address, port));
        SaTempUtil.deleteToken(map.get("token"));
    }
}
