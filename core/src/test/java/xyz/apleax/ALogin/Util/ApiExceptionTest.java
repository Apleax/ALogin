package xyz.apleax.ALogin.Util;

import org.junit.jupiter.api.Test;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ApiException 五个静态工厂的文案/数据/cause 语义
 */
class ApiExceptionTest {

    @Test
    void ofUsesCodeMessageByDefault() {
        assertEquals(ErrorCode.NOT_LOGIN.message(),
                ApiException.of(ErrorCode.NOT_LOGIN).description());
    }

    @Test
    void ofFormatsStringTemplateArg() {
        assertEquals("邮箱或密码错误",
                ApiException.of(ErrorCode.CREDENTIAL_MISMATCH, "邮箱").description());
    }

    @Test
    void ofFormatsIntegerTemplateArg() {
        assertEquals("7日后可以再次使用该昵称",
                ApiException.of(ErrorCode.NICKNAME_REUSE_COOLDOWN, 7L).description());
    }

    @Test
    void withDataCarriesPayload() {
        ApiException e = ApiException.withData(ErrorCode.VERIFY_CODE_FREQUENT, 42L);
        assertEquals(42L, e.data());
        assertEquals("获取失败", e.description());
    }

    @Test
    void rawKeepsDescriptionVerbatim() {
        assertEquals("字段 x 非法",
                ApiException.raw(ErrorCode.PARAM_INVALID, "字段 x 非法").description());
    }

    @Test
    void causedByKeepsOriginalCause() {
        Throwable cause = new IllegalStateException("boom");
        assertSame(cause, ApiException.causedBy(ErrorCode.INTERNAL_ERROR, cause).getCause());
    }
}
