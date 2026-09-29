package xyz.apleax.ALogin.POJO;

/**
 * 正版授权流程状态
 *
 * @param status  pending（等待玩家授权）/ success / error
 * @param message 失败原因（成功时为 null）
 * @param name    授权成功的正版名称（等待或失败时为 null）
 * @author Apleax
 */
public record PremiumFlowStatus(String status, String message, String name) {
    public static final String PENDING = "pending";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
}
