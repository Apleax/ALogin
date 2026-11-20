package xyz.apleax.ALogin.Enum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 解绑请求参数（type）到 {@link BindType} 的映射：大小写不敏感，未知/空值一律返回 null 交由调用方报错
 */
class BindTypeTest {

    @Test
    void lowerCaseKeysMatch() {
        assertEquals(BindType.JAVA, BindType.getValueByKey("java"));
        assertEquals(BindType.BEDROCK, BindType.getValueByKey("bedrock"));
    }

    @Test
    void upperCaseKeyMatches() {
        assertEquals(BindType.JAVA, BindType.getValueByKey("JAVA"));
    }

    @Test
    void unknownKeyReturnsNull() {
        assertNull(BindType.getValueByKey("x"));
    }

    @Test
    void nullKeyReturnsNull() {
        assertNull(BindType.getValueByKey(null));
    }
}
