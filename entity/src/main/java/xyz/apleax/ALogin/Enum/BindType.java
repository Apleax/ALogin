package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 可解绑的绑定类型（接口请求参数维度）。
 * <p>
 * 与 {@link AccountType} 区分：这里是"玩家要解绑哪一种绑定"的请求参数，只有正版与基岩版两种；
 * {@code AccountType} 是登录凭证/缓存索引维度，包含邮箱、账号等与绑定无关的取值。
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
