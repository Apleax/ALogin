package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 账号类型
 *
 * @author Apleax
 */
@Getter
public enum AccountType {
    ACCOUNT("account"),
    EMAIL("email"),
    QQ_ACCOUNT("qq_account"),
    MC_UUID("mc_uuid");
    private final String key;

    AccountType(String key) {
        this.key = key;
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