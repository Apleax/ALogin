package xyz.apleax.ALogin.Entity.VO;

import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 注册接口返回的VO
 *
 * @author Apleax
 */
@AllArgsConstructor
@Getter
@Setter
public class RegisterVO {
    /**
     * 账号
     */
    private String account;
    /**
     * 注册成功时返回的AToken
     */
    private SaTokenInfo token;
}
