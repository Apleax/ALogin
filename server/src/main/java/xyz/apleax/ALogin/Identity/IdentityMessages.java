package xyz.apleax.ALogin.Identity;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Managed;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * 身份绑定功能的最小消息本地化入口。
 *
 * @author Apleax
 */
@Managed
public final class IdentityMessages {
    private static final String BUNDLE_NAME = "ALogin.identity.messages";
    private final ResourceBundle bundle;

    public IdentityMessages() {
        String configuredLocale = Solon.cfg().get("identity.locale", "zh-CN");
        Locale locale = Locale.forLanguageTag(configuredLocale.replace('_', '-'));
        this.bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }

    public String text(String key, String fallback) {
        if (key == null || key.isBlank()) return fallback;
        try {
            return bundle.getString(key);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
