package xyz.apleax.ALogin.VO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * @author Apleax
 */
@JsonSubTypes({
        @JsonSubTypes.Type(value = LoginByEmailVO.class, name = "Email"),
        @JsonSubTypes.Type(value = LoginByMcUuidVO.class, name = "McUuid")
        , @JsonSubTypes.Type(value = LoginByAccountVO.class, name = "Account")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface LoginVO {
}
