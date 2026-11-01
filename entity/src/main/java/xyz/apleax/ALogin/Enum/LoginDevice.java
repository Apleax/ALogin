package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 登录设备（终端）类型。
 * <p>
 * 这是 Sa-Token 的 loginDeviceType 维度（{@code StpUtil.login(id, device)} 的第二个参数），只决定
 * {@code sa-token.is-concurrent=false} 时"同端互斥"的范围：同一 device 的新登录会顶掉旧登录，
 * 不同 device 之间互不影响。
 * <p>
 * 它<b>不是</b>登录凭证类型（邮箱/账号/正版 UUID/基岩 XUID，见 {@link AccountType}）。把凭证类型当 device
 * 传入会让顶号边界随"玩家用哪种方式登录"漂移（同一个 {@code email} 会同时覆盖网页、Java 客户端与基岩表单
 * 三个真实终端）。凭证类型请用 {@code StpUtil.getTokenSession().set("loginMethod", ...)} 记录，仅作审计。
 *
 * @author Apleax
 */
@Getter
public enum LoginDevice {
    WEB("web", "网页/前端"),
    GAME("game", "Java 版游戏客户端"),
    BEDROCK("bedrock", "基岩版客户端");

    private final String key;
    private final String value;

    LoginDevice(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @SuppressWarnings("unused")
    public static LoginDevice getValueByKey(String key) {
        for (LoginDevice value : values()) if (value.key.equals(key)) return value;
        return null;
    }

    @SuppressWarnings("unused")
    public static String getKeyByValue(LoginDevice value) {
        for (LoginDevice v : values()) if (v.equals(value)) return v.key;
        return null;
    }
}
