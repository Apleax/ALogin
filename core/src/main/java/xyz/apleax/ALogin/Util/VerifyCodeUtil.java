package xyz.apleax.ALogin.Util;

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.POJO.VerifyCodePOJO;

/**
 * 验证码工具：只负责验证码的缓存/校验；邮件发送委托给 {@link MailUtil#sendVerifyCodeAsync}。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public final class VerifyCodeUtil {
    @Inject("VerifyCode")
    private static LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache;

    public VerifyCodeUtil() {
    }

    /**
     * 异步发送验证码邮件
     *
     * @param email      收件人邮箱
     * @param verifyCode 验证码
     */
    public static void sendAsync(String email, String verifyCode) {
        MailUtil.sendVerifyCodeAsync(email, verifyCode);
    }

    /**
     * 校验验证码
     *
     * @param key        验证码缓存键
     * @param verifyCode 验证码
     * @return 是否校验失败（true = 失败，false = 通过）
     */
    public static boolean checkVerifyCode(VerifyCodeKey key, String verifyCode) {
        VerifyCodePOJO code = verifyCodeCache.getIfPresent(key);
        if (code == null) {
            log.debug("验证码校验失败：缓存无对应记录（可能已过期、服务重启或从未发送），key={}", key);
            return true;
        }
        if (verifyCode == null || !code.getVerifyCode().equals(verifyCode.trim())) {
            log.debug("验证码校验失败：验证码不匹配，key={}", key);
            return true;
        }
        verifyCodeCache.invalidate(key);
        return false;
    }
}
