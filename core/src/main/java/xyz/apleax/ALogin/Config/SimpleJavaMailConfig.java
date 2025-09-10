package xyz.apleax.ALogin.Config;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.bean.LifecycleBean;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;

/**
 * SimpleJavaMail配置类
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public record SimpleJavaMailConfig() implements LifecycleBean {

    @Override
    public void start() {
        log.info("SimpleJavaMailConfig Loading Complete");
    }

    // 邮件服务器地址
    @Inject("${EmailConfig.HostName}")
    private static String HostName;
    // 邮件服务器端口
    @Inject("${EmailConfig.Port}")
    private static Integer Port;
    // 邮件服务器登录认证信息: 用户名
    @Inject("${EmailConfig.Authentication.UserName}")
    private static String UserName;
    // 邮件服务器登录认证信息: 密码
    @Inject("${EmailConfig.Authentication.Password}")
    private static String Password;

    @Bean(index = -100)
    public Mailer SimpleJavaMailBuilder() {
        log.info("SimpleJavaMailConfig Loading...");
        MailerRegularBuilderImpl mailerRegularBuilder = MailerBuilder
                .withSMTPServer(HostName, Port, UserName, Password);
        switch (Port) {
            case 465:
                mailerRegularBuilder.withTransportStrategy(TransportStrategy.SMTPS);
                break;
            case 587:
                mailerRegularBuilder.withTransportStrategy(TransportStrategy.SMTP_TLS);
                break;
            case 25:
                mailerRegularBuilder.withTransportStrategy(TransportStrategy.SMTP);
                break;
            default:
                throw new IllegalArgumentException("Mail server port error: " + Port);
        }
        return mailerRegularBuilder.buildMailer();
    }
}
