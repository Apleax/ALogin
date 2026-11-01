package xyz.apleax.ALogin.POJO;

import lombok.Data;

/**
 * 验证码缓存项：验证码、发送时间与校验失败计数
 *
 * @author Apleax
 */
@Data
public class VerifyCodePOJO {
    private String verifyCode;
    private Long time;
    private int failedAttempts;

    public VerifyCodePOJO(String verifyCode, Long time) {
        this.verifyCode = verifyCode;
        this.time = time;
    }

    /**
     * 记录一次校验失败
     *
     * @return 是否达到锁定阈值
     */
    public synchronized boolean recordFailure(int maxAttempts) {
        return ++failedAttempts >= maxAttempts;
    }
}