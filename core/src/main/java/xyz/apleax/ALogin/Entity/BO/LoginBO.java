package xyz.apleax.ALogin.Entity.BO;

import lombok.Data;

/**
 * @author Apleax
 */
@Data
public class LoginBO {
    private String account;
    private String mc_uuid;
    private String qq_account;
    private String email;
    private String password;
}