package xyz.apleax.ALogin.Util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.util.ResourceUtil;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件发送统一工具：负责模板加载、占位符替换、异步发送。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public final class MailUtil {
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private static final String TPL_VERIFY_CODE = "email/RegVerifyCode.html";
    private static final String TPL_PASSWORD_RESET_NOTIFY = "email/PasswordResetNotify.html";
    private static final String TPL_SKIN_UPLOAD_FAILED = "email/SkinUploadFailed.html";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 邮件工具
    @Inject
    private static Mailer mailer;
    // 发件人邮箱
    @Inject("${EmailConfig.From.Email}")
    private static String fromEmail;
    // 发件人昵称
    @Inject("${EmailConfig.From.Name}")
    private static String fromName;
    // 服务器名称
    @Getter
    @Inject("${ServerName}")
    private static String serverName;

    public MailUtil() {
    }

    public static void clearTemplateCache() {
        TEMPLATE_CACHE.clear();
    }

    /**
     * 异步发送注册/重置密码验证码邮件。
     *
     * @param toEmail    收件人邮箱
     * @param verifyCode 验证码
     */
    public static boolean sendVerifyCodeAsync(String toEmail, String verifyCode) {
        String verifyCodeSubject = "来自 [" + serverName + "] 的验证码";
        return sendTemplateAsync(toEmail, verifyCodeSubject, TPL_VERIFY_CODE, Map.of(
                "<generatedcode/>", verifyCode,
                "<time/>", LocalDate.now().format(DATE_FMT)
        ));
    }

    /**
     * 异步发送密码重置成功通知邮件。
     *
     * @param toEmail 收件人邮箱
     * @param account 账号
     */
    public static void sendPasswordResetNotifyAsync(String toEmail, String account) {
        String PasswordResetNotifySubject = "[" + serverName + "] 密码重置成功";
        sendTemplateAsync(toEmail, PasswordResetNotifySubject, TPL_PASSWORD_RESET_NOTIFY, Map.of(
                "<account/>", account,
                "<time/>", LocalDateTime.now().format(DATETIME_FMT)
        ));
    }

    /**
     * 异步发送皮肤上传失败提醒邮件。
     *
     * @param toEmail 收件人邮箱（为空则跳过）
     * @param account 账号
     * @param reason  用户可读的失败原因
     */
    public static void sendSkinUploadFailedAsync(String toEmail, String account, String reason) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("收件人为空，跳过皮肤上传失败提醒");
            return;
        }
        String subject = "[" + serverName + "] 皮肤上传失败";
        sendTemplateAsync(toEmail, subject, TPL_SKIN_UPLOAD_FAILED, Map.of(
                "<account/>", account,
                "<reason/>", reason,
                "<time/>", LocalDateTime.now().format(DATETIME_FMT)
        ));
    }


    /**
     * 异步发送模板邮件。
     *
     * @param toEmail              收件人邮箱
     * @param subject              邮件主题
     * @param templateRelativePath 模板相对于 {appName}/ 的路径，如 "email/RegVerifyCode.html"
     * @param placeholders         占位符 → 替换值；{@code <servername/>} 会自动替换，无需放入
     * @return 是否成功提交发送（不代表邮件已投递）
     */
    public static boolean sendTemplateAsync(String toEmail, String subject,
                                            String templateRelativePath,
                                            Map<String, String> placeholders) {
        return sendTemplateAsync(toEmail, subject, templateRelativePath, placeholders, null);
    }

    /**
     * 异步发送模板邮件；模板加载失败时使用 fallbackHtml 降级
     */
    public static boolean sendTemplateAsync(String toEmail, String subject,
                                            String templateRelativePath,
                                            Map<String, String> placeholders,
                                            String fallbackHtml) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("收件人为空，跳过邮件发送");
            return false;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("发件人邮箱未配置，无法发送邮件，收件人: {}", toEmail);
            return false;
        }
        String html = buildHtml(templateRelativePath, placeholders, fallbackHtml);
        if (html == null || html.isBlank()) {
            log.warn("邮件正文为空，跳过发送，收件人: {}", toEmail);
            return false;
        }
        try {
            mailer.sendMail(EmailBuilder.startingBlank()
                                    .from(fromName, fromEmail)
                                    .withSubject(subject)
                                    .to(toEmail)
                                    .withHTMLText(html)
                                    .buildEmail()
                            , true)
                    .whenComplete((_, throwable) -> {
                        if (throwable != null)
                            log.warn("邮件发送失败，收件人: {}，主题: {}，原因: {}", toEmail, subject, throwable.getMessage());
                        else log.debug("邮件发送成功，收件人: {}，主题: {}", toEmail, subject);
                    });
            return true;
        } catch (Exception e) {
            log.warn("邮件提交失败，收件人: {}，主题: {}，原因: {}", toEmail, subject, e.getMessage());
            return false;
        }
    }

    private static String buildHtml(String templateRelativePath, Map<String, String> placeholders, String fallbackHtml) {
        String template = loadTemplate(templateRelativePath);
        String source = (template != null && !template.isBlank()) ? template : fallbackHtml;
        if (source == null) return null;
        String result = source.replace("<servername/>", safe(serverName));
        if (placeholders != null) for (Map.Entry<String, String> entry : placeholders.entrySet())
            result = result.replace(entry.getKey(), safe(entry.getValue()));
        return result;
    }

    /**
     * 加载模板并缓存；优先读外部目录，回退到 classpath。加载失败缓存空串避免重复 IO。
     */
    private static String loadTemplate(String templateRelativePath) {
        String appName = Solon.cfg().appName();
        String fullPath = appName + "/" + templateRelativePath;
        return TEMPLATE_CACHE.computeIfAbsent(fullPath, path -> {
            try {
                URL externalUrl = ResourceUtil.getResourceByFile("./" + path);
                if (externalUrl != null) {
                    String content = ResourceUtil.getResourceAsString(externalUrl);
                    return content == null ? "" : content;
                }
                String content = ResourceUtil.getResourceAsString(path);
                return content == null ? "" : content;
            } catch (Throwable t) {
                // 包括 IOException 以及 ResourceUtil 在资源缺失时可能抛出的 RuntimeException
                log.warn("邮件模板加载失败 ({}): {}", path, t.getMessage());
                return "";
            }
        });
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
