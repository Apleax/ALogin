package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 账号类型
 *
 * @author Apleax
 */
@Getter
public enum AccountType {
    ACCOUNT("account", "账号"),
    EMAIL("email", "邮箱"),
    QQ_ACCOUNT("qq_account", "QQ号"),
    MC_UUID("mc_uuid", "MCUUID");
    private final String key;
    private final String value;

    AccountType(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static AccountType getValueByKey(String key) {
        for (AccountType value : values()) if (value.key.equals(key)) return value;
        return null;
    }

    public static String getKeyByValue(AccountType value) {
        for (AccountType v : values()) if (v.equals(value)) return v.key;
        return null;
    }
}