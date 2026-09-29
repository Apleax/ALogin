package xyz.apleax.ALogin.VO;

/**
 * 用户信息（premiumUuid 非空表示已绑定正版）
 *
 * @author Apleax
 */
public record UserInfoVO(String account, String nickname, String avatar, String premiumUuid, String premiumName) {
}
