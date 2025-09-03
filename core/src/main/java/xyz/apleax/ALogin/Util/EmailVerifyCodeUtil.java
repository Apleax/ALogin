package xyz.apleax.ALogin.Util;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.util.ResourceUtil;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件发送工具
 *
 * @author Apleax
 */
@Slf4j
@Component
public final class EmailVerifyCodeUtil {
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

    /**
     * 构建邮件
     *
     * @param email      收件人邮箱
     * @param verifyCode 验证码
     * @author Apleax
     */
    public static void sendAsync(String email, String verifyCode) {
        try {
            String VCodeHTML = ResourceUtil.getResourceAsString(Solon.cfg().appName() + "/email/RegVerifyCode.html")
                    .replace("<servername/>", Server)
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
            resultFuture.whenComplete((result, throwable) -> {
                if (throwable != null) log.warn("邮件发送失败，收件人: {}，原因: {}", email, throwable.getMessage());
                else log.debug("邮件发送成功，收件人: {}", email);
            });
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
        }
    }
}
