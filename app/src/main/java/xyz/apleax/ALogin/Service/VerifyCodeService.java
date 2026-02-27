package xyz.apleax.ALogin.Service;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;

/**
 * 验证码Service
 *
 * @author Apleax
 */
@DamiTopic("account.verifycode")
public interface VerifyCodeService {
    /**
     * 邮箱验证码
     *
     * @param verifyCodeKey 验证码参数
     * @author Apleax
     */
    Result<Long> verifyCode(VerifyCodeKey verifyCodeKey);
}
