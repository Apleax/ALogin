package xyz.apleax.ALogin.Entity.POJO;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Apleax
 */
@Data
@AllArgsConstructor
public class VerifyCodePOJO {
    private String verifyCode;
    private Long time;
}