package xyz.apleax.ALogin.Enum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link BindType} 的参数映射
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
