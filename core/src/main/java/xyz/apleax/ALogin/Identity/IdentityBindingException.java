package xyz.apleax.ALogin.Identity;

/**
 * 外部身份绑定失败。
 *
 * @author Apleax
 */
public class IdentityBindingException extends RuntimeException {
    private final String messageKey;

    public IdentityBindingException(String messageKey, String fallbackMessage) {
        super(fallbackMessage);
        this.messageKey = messageKey;
    }

    public IdentityBindingException(String messageKey, String fallbackMessage, Throwable cause) {
        super(fallbackMessage, cause);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
