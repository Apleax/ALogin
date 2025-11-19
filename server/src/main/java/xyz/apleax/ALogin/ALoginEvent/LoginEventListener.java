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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("LoginEvent")
public class LoginEventListener implements EventListener<Map<String, String>> {
    private static final String transfer = Solon.cfg().get("minestom.transfer");

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        UUID value = SaTempUtil.parseToken(map.get("token"), UUID.class);
        if (value == null) return;
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(value);
        if (player == null) return;
        String address = transfer.split(":")[0];
        int port = Integer.parseInt(transfer.split(":")[1]);
        player.getPlayerConnection().storeCookie("alogin:token", StpUtil.getTokenValueByLoginId(map.get("account")).getBytes(StandardCharsets.UTF_8));
        player.sendPacket(new TransferPacket(address, port));
        SaTempUtil.deleteToken(map.get("token"));
    }
}
