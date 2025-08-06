package xyz.apleax.ALogin.Enum;

import lombok.Getter;

/**
 * 登陆设备类型
 *
 * @author Apleax
 */
@Getter
public enum LoginDeviceType {
    BROWSER("Browser");
    private final String key;

    LoginDeviceType(String key) {
        this.key = key;
    }

    public static LoginDeviceType getValueByKey(String key) {
        for (LoginDeviceType value : LoginDeviceType.values()) if (value.key.equals(key)) return value;
        return null;
    }

    public static String getKeyByValue(LoginDeviceType value) {
        for (LoginDeviceType v : LoginDeviceType.values()) if (v.equals(value)) return v.key;
        return null;
    }
}
