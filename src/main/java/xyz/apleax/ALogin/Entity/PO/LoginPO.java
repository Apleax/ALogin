package xyz.apleax.ALogin.Entity.PO;

import lombok.Data;

/**
 * @author Apleax
 */
@Data
public class LoginPO {
    private String account;
    private String email;
    private String password;
    private String mc_uuid;
    private String qq_account;
}
