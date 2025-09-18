package xyz.apleax.ALogin.VO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * @author Apleax
 */
@JsonSubTypes({
        @JsonSubTypes.Type(value = RegisterVerifyCodeVO.class, name = "Register"),
        @JsonSubTypes.Type(value = ResetPasswordVerifyCodeVO.class, name = "ResetPassword")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface VerifyCodeVO {
}