package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.POJO.GameProfile;

/**
 * 登录Service
 *
 * @author Apleax
 */
@DamiTopic("account.login")
public interface LoginService {
    /**
     * 登录
     *
     * @param loginBO 登录信息
     * @author Apleax
     */
    Result<SaTokenInfo> login(LoginBO loginBO, String token);

    /**
     * 校验Token
     *
     * @param token Token
     * @return GameProfile
     */
    GameProfile checkToken(String token);
}
