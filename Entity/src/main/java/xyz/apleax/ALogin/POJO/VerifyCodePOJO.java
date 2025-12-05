package xyz.apleax.ALogin.POJO;

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