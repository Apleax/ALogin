package xyz.apleax.ALogin.Util;

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.util.ResourceUtil;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.POJO.VerifyCodePOJO;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件发送工具
 *
 * @author Apleax
 */
@Slf4j
@Managed
public final class VerifyCodeUtil {
    // 邮件工具
    @Inject
    private static Mailer mailer;
    // 发送人邮箱
    @Inject("${EmailConfig.From.Email}")
    private static String FromEmail;
    // 发送人昵称
    @Inject("${EmailConfig.From.Name}")
    private static String FromName;
    //邮件主题
    @Inject("${EmailConfig.Subject}")
    private static String Subject;
    // 服务器名称
    @Inject("${ServerName}")
    private static String Server;
    // 验证码缓存
    @Inject("VerifyCode")
    private static LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> verifyCodeCache;

    private static final String appName = Solon.cfg().appName();
    private static final URL emailTemplateFile = ResourceUtil.getResourceByFile("./" + appName + "/email/RegVerifyCode.html");
    private static String VCodeHTML;

    static {
        try {
            if (emailTemplateFile == null)
                VCodeHTML = ResourceUtil.getResourceAsString(Solon.cfg().appName() + "/email/RegVerifyCode.html");
            else VCodeHTML = ResourceUtil.getResourceAsString(emailTemplateFile);
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
        }
    }

    /**
     * 构建邮件发送
     *
     * @param email      收件人邮箱
     * @param verifyCode 验证码
     * @author Apleax
     */
    public static void sendAsync(String email, String verifyCode) {
        VCodeHTML = VCodeHTML.replace("<servername/>", Server)
                .replace("<generatedcode/>", verifyCode)
                .replace("<time/>", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        CompletableFuture<Void> resultFuture = mailer.sendMail(
                EmailBuilder
                        .startingBlank()
                        .from(FromName, FromEmail)
                        .withSubject(Subject)
                        .to(email)
                        .withHTMLText(VCodeHTML)
                        .buildEmail()
                , true);
        resultFuture.whenComplete((_, throwable) -> {
            if (throwable != null) log.warn("邮件发送失败，收件人: {}，原因: {}", email, throwable.getMessage());
            else log.debug("邮件发送成功，收件人: {}", email);
        });
    }

    /**
     * 校验验证码
     *
     * @param key        验证码缓存键
     * @param verifyCode 验证码
     * @return 是否通过校验
     * @author Apleax
     */
    public static boolean checkVerifyCode(VerifyCodeKey key, String verifyCode) {
        VerifyCodePOJO code = verifyCodeCache.get(key);
        if (code == null || !code.getVerifyCode().equals(verifyCode)) return true;
        verifyCodeCache.invalidate(key);
        return false;
    }
}
