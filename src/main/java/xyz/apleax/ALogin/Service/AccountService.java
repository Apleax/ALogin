package xyz.apleax.ALogin.Service;

import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.VO.RegisterVO;

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
    Result<RegisterVO> register(RegisterDTO registerDTO) throws Exception;

    /**
     * 邮箱验证码
     *
     * @param email 邮箱
     * @author Apleax
     */
    Result<Long> emailVerifyCode(String email);
}
