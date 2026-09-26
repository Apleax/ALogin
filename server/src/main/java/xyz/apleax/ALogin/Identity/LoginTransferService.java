package xyz.apleax.ALogin.Identity;

import cn.dev33.satoken.stp.StpUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.TransferPacket;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;

import java.nio.charset.StandardCharsets;

/**
 * 统一复用 ALogin 现有的 Cookie + Transfer 登录完成动作。
 *
 * @author Apleax
 */
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginTransferService {
    private final String transfer = Solon.cfg().get("minestom.transfer", "localhost:25566");
    private final String cookieKey = Solon.cfg().get("minestom.cookie-key", "alogin");

    public void transfer(Player player, String account) {
        if (player == null || account == null || account.isBlank()) return;
        String token = StpUtil.getTokenValueByLoginId(account);
        if (token == null || token.isBlank()) return;
        String address = transfer;
        int port = 25565;
        int separator = transfer.lastIndexOf(':');
        if (separator > 0 && separator < transfer.length() - 1) {
            address = transfer.substring(0, separator);
            port = Integer.parseInt(transfer.substring(separator + 1));
        }
        player.getPlayerConnection().storeCookie(
                cookieKey + ":token", token.getBytes(StandardCharsets.UTF_8));
        player.sendPacket(new TransferPacket(address, port));
    }
}
