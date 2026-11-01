package xyz.apleax.ALogin.POJO;

/**
 * 正版授权的设备码信息
 *
 * @param flowKey          本次授权流程标识，用于轮询 {@link PremiumFlowStatus}
 * @param userCode         用户码（展示给玩家）
 * @param verificationUri  授权页面地址
 * @param expiresInSeconds userCode 有效期（秒）
 * @author Apleax
 */
public record PremiumDeviceCode(String flowKey, String userCode, String verificationUri, long expiresInSeconds) {
}
