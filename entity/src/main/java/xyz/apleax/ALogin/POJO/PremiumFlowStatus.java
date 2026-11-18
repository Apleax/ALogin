package xyz.apleax.ALogin.POJO;

/**
 * 正版授权流程状态
 *
 * @param status  pending（等待玩家授权）/ success / error
 * @param message 失败原因（成功时为 null）
 * @param name    授权成功的正版名称（等待或失败时为 null）
 * @param code    业务码（成功/等待为 200，失败为失败码）
 * @author Apleax
 */
public record PremiumFlowStatus(String status, String message, String name, Integer code) {
    public static final String PENDING = "pending";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
}
