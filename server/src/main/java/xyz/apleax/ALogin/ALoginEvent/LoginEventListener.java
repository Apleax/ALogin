package xyz.apleax.ALogin.ALoginEvent;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.noear.dami2.Dami;
import org.noear.dami2.bus.Event;
import org.noear.dami2.bus.EventListener;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;
import xyz.apleax.ALogin.Util.TransferAddress;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 登录放行事件：写入进服 cookie 并转移玩家到代理入口
 *
 * @author Apleax
 */
@Slf4j
@DamiTopic("LoginEvent")
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginEventListener implements EventListener<Map<String, String>> {
    private static final TransferAddress transfer = TransferAddress.parse(Solon.cfg().get("minestom.transfer"));
    private static final String cookieKey = Solon.cfg().get("minestom.cookie-key");

    private final PremiumAssertionService assertionService;

    public LoginEventListener(PremiumAssertionService assertionService) {
        this.assertionService = assertionService;
    }

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        UUID value = SaTempUtil.parseToken(map.get("token"), UUID.class);
        if (value == null) return;
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(value);
        if (player == null) return;
        String account = map.get("account");
        if (account == null) return;
        // 首次绑定：本次会话是正版时把正版 UUID 写入该账号（无断言则不触发）
        PremiumAssertion assertion = assertionService.get(player.getUuid());
        if (assertion != null)
            Dami.bus().send("PremiumBind", Map.of("account", account,
                    "premiumUuid", assertion.uuid().toString(),
                    "premiumName", assertion.name() == null ? "" : assertion.name()));
        String tokenValue = StpUtil.getTokenValueByLoginId(account);
        if (tokenValue == null) {
            log.warn("登录放行失败：无法获取会话 token，account={}", account);
            return;
        }
        player.getPlayerConnection().storeCookie(cookieKey + ":token", tokenValue.getBytes(StandardCharsets.UTF_8));
        player.getPlayerConnection().transfer(transfer.host(), transfer.port());
        SaTempUtil.deleteToken(map.get("token"));
    }
}
