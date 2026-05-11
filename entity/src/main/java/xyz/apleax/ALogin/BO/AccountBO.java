package xyz.apleax.ALogin.BO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.apleax.ALogin.POJO.GameProfile;

import java.util.List;
import java.util.UUID;

/**
 * @author Apleax
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountBO {
    private String account;
    private String algorithm;
    private String avatar;
    private Long bindQqAccountTime;
    private String email;
    private UUID mcUuid;
    private List<GameProfile.Property> properties;
    private String nickName;
    private String password;
    private Long qqAccount;
    private Long registrationTime;
    private String salt;
    private String lastLoginIp;
}
