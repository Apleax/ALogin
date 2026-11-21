package xyz.apleax.ALogin.VO;

/**
 * 用户配置（自动登录开关）。缺省即开启。
 *
 * @author Apleax
 */
public record UserConfigVO(boolean autoLoginJava, boolean autoLoginBedrock) {
}
