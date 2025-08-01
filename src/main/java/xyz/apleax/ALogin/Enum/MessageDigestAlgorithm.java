package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 摘要算法枚举
 *
 * @author Apleax
 */
@Getter
public enum MessageDigestAlgorithm {

    SHA256("SHA-256"),

    SHA512("SHA-512");

    private final String key;

    MessageDigestAlgorithm(String key) {
        this.key = key;
    }

    public static MessageDigestAlgorithm getValueByKey(String key) {
        for (MessageDigestAlgorithm value : values()) if (value.key.equals(key)) return value;
        return null;
    }

    public static String getKeyByValue(MessageDigestAlgorithm value) {
        for (MessageDigestAlgorithm entry : values()) if (entry.equals(value)) return entry.key;
        return null;
    }
}