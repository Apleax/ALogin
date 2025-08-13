package xyz.apleax.ALogin.Service;

import cn.dev33.satoken.stp.SaTokenInfo;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Entity.DTO.LoginByEmailDTO;
import xyz.apleax.ALogin.Entity.DTO.LoginByMcUuidDTO;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;

/**
 * 账号Service
 *
 * @author Apleax
 */
public interface AccountService {
    /**
     * 注册
     *
     * @param registerDTO 注册信息: 邮箱 密码 验证码
     * @return 注册结果
     * @author Apleax
     */
    Result<SaTokenInfo> register(RegisterDTO registerDTO, Context context) throws Exception;

    /**
     * 邮箱验证码
     *
     * @param email 邮箱
     * @author Apleax
     */
    Result<Long> registerVerifyCode(String email);

    /**
     * 通过邮箱登录
     *
     * @param loginByEmailDTO 登录信息: 邮箱 密码
     * @author Apleax
     */
    Result<SaTokenInfo> loginByEmail(LoginByEmailDTO loginByEmailDTO) throws Exception;

    /**
     * 通过Minecraft UUID登录
     *
     * @param loginByMcUuidDTO 登录信息: Minecraft_UUID 密码
     * @author Apleax
     */
    Result<SaTokenInfo> loginByMcUuid(LoginByMcUuidDTO loginByMcUuidDTO) throws Exception;
}
