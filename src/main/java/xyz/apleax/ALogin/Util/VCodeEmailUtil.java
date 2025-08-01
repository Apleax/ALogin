package xyz.apleax.ALogin.Util;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.util.ResourceUtil;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 邮件发送工具
 *
 * @author Apleax
 */
@Slf4j
@Component
public final class VCodeEmailUtil {
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
     * @param userName 收件人昵称，默认"User"
     * @param email    收件人邮箱
     * @param VCode    验证码
     * @author Apleax
     */
    private static void VCodeEmailBuilder(String userName, String email, String VCode) {
        try {
            String VCodeHTML = ResourceUtil.getResourceAsString("ExaminationSystem/email/VCode.html")
                    .replace("<servername/>", Server)
                    .replace("<username/>", userName)
                    .replace("<generatedcode/>", VCode)
                    .replace("<time/>", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            mailer.sendMail(
                    EmailBuilder
                            .startingBlank()
                            .from(FromName, FromEmail)
                            .withSubject(Subject)
                            .to(userName, email)
                            .withHTMLText(VCodeHTML)
                            .buildEmail()
                    , true);
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
        }
    }

    /**
     * 发送邮件
     *
     * @param userName 收件人昵称，默认"User"
     * @param email    收件人邮箱
     * @param VCode    验证码
     * @author Apleax
     */
    public static void send(String userName, String email, String VCode) {
        if (userName == null || userName.isEmpty()) userName = "User";
        VCodeEmailBuilder(userName, email, VCode);
    }
}
