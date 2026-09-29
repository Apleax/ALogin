package xyz.apleax.ALogin.Service;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.POJO.PremiumDeviceCode;
import xyz.apleax.ALogin.POJO.PremiumFlowStatus;

/**
 * 正版（微软设备码授权）服务
 *
 * @author Apleax
 */
@DamiTopic("premium")
public interface PremiumService {
    /**
     * 发起绑定正版账号的设备码授权（需网页已登录）
     */
    Result<PremiumDeviceCode> getBindCode();

    /**
     * 发起"微软账号一键进入"的设备码授权
     *
     * @param token  进服会话临时 token
     * @param realIp 来源 IP
     */
    Result<PremiumDeviceCode> getLoginCode(String token, String realIp);

    /**
     * 查询授权流程状态（前端轮询用）
     *
     * @param flowKey 发起授权时返回的流程标识
     */
    Result<PremiumFlowStatus> getStatus(String flowKey);

    /**
     * 已绑定正版 UUID 名单（换行分隔）
     *
     * @param key 共享密钥（须与 Premium.assertion.secret 一致）
     */
    Result<String> getBoundUuids(String key);
}
