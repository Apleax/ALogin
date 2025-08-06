package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 账号查询可用的的唯一条件
 *
 * @author Apleax
 */
@Getter
public enum AccountIndex {
    ACCOUNT("account"),
    EMAIL("email"),
    QQ_ACCOUNT("qq_account"),
    MC_UUID("mc_uuid");
    private final String key;

    AccountIndex(String key) {
        this.key = key;
    }

    public static AccountIndex getValueByKey(String key) {
        for (AccountIndex value : values()) if (value.key.equals(key)) return value;
        return null;
    }

    public static String getKeyByValue(AccountIndex value) {
        for (AccountIndex v : values()) if (v.equals(value)) return v.key;
        return null;
    }
}