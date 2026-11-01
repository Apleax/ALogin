package xyz.apleax.ALogin.BO;

import lombok.Data;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.LoginDevice;

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
    /**
     * 登录设备（终端）类型，决定同端互斥的范围；由服务端入口判定，不接受客户端自报
     */
    private LoginDevice device;
}