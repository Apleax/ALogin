package xyz.apleax.ALogin.ALoginEvent;

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
        // 首次绑定兜底：本次会话是正版时把正版 UUID 写入该账号（无断言则不触发）。
        // 正版自动登录（premiumBound=true）的账号本来就是按 premium_uuid 查出来的，
        // 绑定关系已成立、名字也已同步，再走一次只会得到"重复绑定（幂等）"，直接跳过
        PremiumAssertion assertion = assertionService.get(player.getUuid());
        if (assertion != null && !"true".equals(map.get("premiumBound")))
            Dami.bus().send("PremiumBind", Map.of("account", account,
                    "premiumUuid", assertion.uuid().toString(),
                    "premiumName", assertion.name() == null ? "" : assertion.name()));
        String tokenValue = map.get("sessionToken");
        if (tokenValue == null || tokenValue.isBlank()) {
            log.warn("登录放行失败：事件缺少 sessionToken，account={}", account);
            // 放行不可能发生，临时 token 没有保留价值；不删会留下最长 5 分钟的可重放凭证
            SaTempUtil.deleteToken(map.get("token"));
            return;
        }
        player.getPlayerConnection().storeCookie(cookieKey + ":token", tokenValue.getBytes(StandardCharsets.UTF_8));
        player.getPlayerConnection().transfer(transfer.host(), transfer.port());
        SaTempUtil.deleteToken(map.get("token"));
    }
}
