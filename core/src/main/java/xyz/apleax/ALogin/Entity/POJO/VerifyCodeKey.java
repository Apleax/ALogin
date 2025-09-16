package xyz.apleax.ALogin.Entity.POJO;

import xyz.apleax.ALogin.Enum.VerifyCodeType;

/**
 * @param email 邮箱
 * @param type  验证码用途枚举
 * @author Apleax
 */
public record VerifyCodeKey(String email, VerifyCodeType type) {
}
