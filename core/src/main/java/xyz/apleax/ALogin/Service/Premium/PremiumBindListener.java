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
 * 首次绑定：密码/网页登录成功后触发，把本次会话已验证的正版 UUID 写入该账号
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("PremiumBind")
public class PremiumBindListener implements EventListener<Map<String, String>> {
    private final PremiumServiceImpl premiumService;

    public PremiumBindListener(PremiumServiceImpl premiumService) {
        this.premiumService = premiumService;
    }

    @Override
    public void onEvent(Event<Map<String, String>> event) {
        Map<String, String> map = event.getPayload();
        try {
            premiumService.bind(map.get("account"), new PremiumProfile(
                    UUID.fromString(map.get("premiumUuid")), map.getOrDefault("premiumName", "")));
        } catch (Exception e) {
            log.warn("登录后正版绑定处理失败：{}", e.getMessage());
        }
    }
}
