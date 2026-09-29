package xyz.apleax.ALogin.POJO;

import java.util.UUID;

/**
 * 正版断言：ViaProxy 插件经登录插件消息回传的正版身份
 *
 * @param uuid 正版 UUID
 * @param name 正版名字
 * @param ip   真实客户端 IP（可空）
 * @param ts   断言签发时间（秒）
 * @author Apleax
 */
public record PremiumAssertion(UUID uuid, String name, String ip, long ts) {
}
