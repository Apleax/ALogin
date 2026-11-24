package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 账号绑定类型（解绑与自动登录开关的请求参数）
 *
 * @author Apleax
 */
@Getter
public enum BindType {
    JAVA("java", "正版 Java 账号"),
    BEDROCK("bedrock", "基岩版账号");

    private final String key;
    private final String value;

    BindType(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 按 key 查枚举，大小写不敏感，找不到返回 null
     */
    @SuppressWarnings("unused")
    public static BindType getValueByKey(String key) {
        for (BindType value : values()) if (value.key.equalsIgnoreCase(key)) return value;
        return null;
    }
}
