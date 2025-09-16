package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.Entity.BO.LoginBO;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodeKey;
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
     * @param accountBO   注册信息
     * @param verify_code 验证码
     * @param real_ip     真实ip
     * @author Apleax
     */
    Result<SaTokenInfo> register(AccountBO accountBO, String verify_code, String real_ip) throws Exception;

    /**
     * 登录
     *
     * @param loginBO     登录信息
     * @param loginIp     登录ip
     * @param accountType 本次登录方式
     * @author Apleax
     */
    Result<SaTokenInfo> login(LoginBO loginBO, String loginIp, AccountType accountType) throws Exception;

    /**
     * 邮箱验证码
     *
     * @param verifyCodeKey 验证码参数
     * @author Apleax
     */
    Result<Long> verifyCode(VerifyCodeKey verifyCodeKey);

    /**
     * 查询登录状态
     *
     * @param ip      本次登录ip
     * @param mc_uuid 玩家mc_uuid
     * @author Apleax
     */
    Result<Boolean> checkLogin(String ip, String mc_uuid);

    /**
     * 获取登录信息
     *
     * @author Apleax
     */
    Result<SaTokenInfo> getLoginInfo();

    /**
     * 登出
     *
     * @author Apleax
     */
    Result<SaTokenInfo> logout();

    /**
     * 重置密码
     *
     * @param email        邮箱
     * @param verify_code  验证码
     * @param new_password 新密码
     * @author Apleax
     */
    Result<Boolean> resetPassword(String email, String verify_code, String new_password) throws Exception;
}
