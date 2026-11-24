package xyz.apleax.ALogin.Error;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一错误码的取值约束：码值/常量名唯一，成功码唯一，业务码区间与 HTTP 状态码约定
 */
class ErrorCodeTest {

    @Test
    void codesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(seen.add(code.code()),
                    "业务码重复：" + code.code() + "（冲突常量 " + code.name() + "）");
        }
    }

    @Test
    void namesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(seen.add(code.name()), "常量名重复：" + code.name());
        }
    }

    @Test
    void succeedIsTheOnlySuccessCode() {
        assertEquals(200, ErrorCode.SUCCEED.code(), "成功码必须恒为 200");
        assertTrue(ErrorCode.SUCCEED.isSuccess(), "SUCCEED.isSuccess() 必须为 true");

        for (ErrorCode code : ErrorCode.values()) {
            if (code == ErrorCode.SUCCEED) continue;
            assertFalse(code.isSuccess(),
                    code.name() + " 不应被视为成功码（code=" + code.code() + "）");
        }
    }

    @Test
    void failureCodesWithinRange() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code == ErrorCode.SUCCEED) continue;
            assertTrue(code.code() >= 400000 && code.code() < 600000,
                    code.name() + " 的业务码 " + code.code() + " 不在 [400000, 600000) 区间");
        }
    }

    @Test
    void onlyClientKeyMismatchUses403() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code == ErrorCode.CLIENT_KEY_MISMATCH) {
                assertEquals(403, code.httpStatus(), "CLIENT_KEY_MISMATCH 必须返回 HTTP 403");
            } else {
                assertEquals(200, code.httpStatus(),
                        code.name() + " 的 HTTP 状态码应为 200，实际 " + code.httpStatus());
            }
        }
    }

    @Test
    void messagesAreNotBlank() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.message(), code.name() + " 的提示文案为 null");
            assertFalse(code.message().isBlank(), code.name() + " 的提示文案为空白");
        }
    }
}
