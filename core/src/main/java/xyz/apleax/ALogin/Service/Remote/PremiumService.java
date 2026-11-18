package xyz.apleax.ALogin.Service.Remote;

import org.noear.dami2.solon.annotation.DamiTopic;
import xyz.apleax.ALogin.POJO.PremiumDeviceCode;
import xyz.apleax.ALogin.POJO.PremiumFlowStatus;

/**
 * 正版（微软设备码授权）服务契约（Dami LPC topic: premium）
 *
 * @author Apleax
 */
@DamiTopic("premium")
public interface PremiumService {
    /**
     * 发起绑定正版账号的设备码授权（需网页已登录）
     */
    PremiumDeviceCode getBindCode();

    /**
     * 发起"微软账号一键进入"的设备码授权
     *
     * @param token  进服会话临时 token
     * @param realIp 来源 IP
     */
    PremiumDeviceCode getLoginCode(String token, String realIp);

    /**
     * 查询授权流程状态（前端轮询用）
     *
     * @param flowKey 发起授权时返回的流程标识
     */
    PremiumFlowStatus getStatus(String flowKey);

    /**
     * 已绑定正版 UUID 名单（换行分隔）
     *
     * @param key 共享密钥（须与 Premium.assertion.secret 一致）
     */
    String getBoundUuids(String key);
}
