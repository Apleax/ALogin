package xyz.apleax.ALogin.Entity.BO;

import lombok.Data;
import xyz.apleax.ALogin.Enum.AccountType;

/**
 * @author Apleax
 */
@Data
public class LoginBO {
    private String account;
    private String qq_account;
    private String email;
    private String password;
    private String real_ip;
    private AccountType account_type;
}