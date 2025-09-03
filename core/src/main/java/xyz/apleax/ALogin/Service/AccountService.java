package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.Entity.BO.LoginBO;
import xyz.apleax.ALogin.Enum.AccountType;

/**
 * 账号Service
 *
 * @author Apleax
 */
public interface AccountService {
    /**
     * 注册
     *
     * @param accountBO 注册信息: 邮箱 密码 验证码
     * @return 注册结果
     * @author Apleax
     */
    Result<SaTokenInfo> register(AccountBO accountBO, String verify_code, String real_ip) throws Exception;

    /**
     * 通过邮箱登录
     *
     * @param loginBO 登录信息: 邮箱 密码
     * @author Apleax
     */
    Result<SaTokenInfo> login(LoginBO loginBO, String loginIp, AccountType accountType) throws Exception;

    /**
     * 邮箱验证码
     *
     * @param email 邮箱
     * @author Apleax
     */
    Result<Long> registerVerifyCode(String email);
}
