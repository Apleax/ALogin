package xyz.apleax.ALogin.Service;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;

/**
 * 基岩版（XUID）免密登录与绑定服务
 *
 * @author Apleax
 */
@DamiTopic("bedrock")
public interface BedrockService {
    /**
     * 已绑定 XUID 的免密登录
     *
     * @param key  共享密钥（须与 Premium.assertion.secret 一致）
     * @param xuid Xbox 账号的 XUID
     * @param name 基岩版游戏名（可为空）
     * @return 可写入进服 cookie 的 sa-token
     */
    Result<String> autoLogin(String key, String xuid, String name);

    /**
     * 把 XUID 绑定到 token 对应的账号
     *
     * @param key   共享密钥（须与 Premium.assertion.secret 一致）
     * @param token 密码登录返回的 sa-token
     * @param xuid  Xbox 账号的 XUID
     * @param name  基岩版游戏名（可为空）
     */
    Result<Boolean> bind(String key, String token, String xuid, String name);
}
