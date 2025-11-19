package xyz.apleax.ALogin.Entity.BO;

import lombok.Data;

import java.util.List;

/**
 * @author Apleax
 */
@Data
public class AccountBO {
    private String account;
    private String algorithm;
    private String avatar;
    private Long bindQqAccountTime;
    private String email;
    private String mcUuid;
    private String nickName;
    private String password;
    private Long qqAccount;
    private Long registrationTime;
    private String salt;
    private String lastLoginIp;
    private List<String> permission;
}
