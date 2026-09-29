package xyz.apleax.ALogin.Service.Premium;

import lombok.extern.slf4j.Slf4j;
import org.noear.dami2.bus.Event;
import org.noear.dami2.bus.EventListener;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.POJO.PremiumProfile;

import java.util.Map;
import java.util.UUID;

/**
 * 正版自动登录尾部：登录服 spawn 阶段发来事件，完成登录并触发 LoginEvent 放行
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("PremiumAutoLogin")
public class PremiumAutoLoginListener implements EventListener<Map<String, String>> {
    private final PremiumServiceImpl premiumService;

    public PremiumAutoLoginListener(PremiumServiceImpl premiumService) {
        this.premiumService = premiumService;
    }

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        try {
            premiumService.autoLogin(map.get("account"),
                    UUID.fromString(map.get("playerUuid")),
                    new PremiumProfile(UUID.fromString(map.get("premiumUuid")),
                            map.getOrDefault("premiumName", "")),
                    emptyToNull(map.get("ip")));
        } catch (Exception e) {
            log.warn("正版自动登录处理失败：{}", e.getMessage());
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
