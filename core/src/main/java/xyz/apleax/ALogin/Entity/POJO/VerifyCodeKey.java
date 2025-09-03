package xyz.apleax.ALogin.Entity.POJO;

import xyz.apleax.ALogin.Enum.VerifyCodeType;

/**
 * @param type 验证码用途枚举
 */
public record VerifyCodeKey(String email, VerifyCodeType type) {
}
