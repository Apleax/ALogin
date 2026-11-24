package xyz.apleax.ALogin.Util;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cause 链穿透工具：Dami LPC 运行时会把业务异常再包一层，业务层必须能找回原始错误码
 */
class ErrorUtilsTest {

    /**
     * 模拟 Dami LPC 派发时对异常的外层包装
     */
    private static final class DamiLikeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DamiLikeException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * getCause() 返回自身的异常，用于验证遍历不会死循环
     */
    private static final class SelfCauseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public Throwable getCause() {
            return this;
        }
    }

    @Test
    void findsApiExceptionDirectly() {
        ApiException e = ApiException.of(ErrorCode.NOT_LOGIN);
        assertSame(e, ErrorUtils.findApiException(e));
    }

    @Test
    void findsApiExceptionInsideRuntimeException() {
        ApiException e = ApiException.of(ErrorCode.NOT_LOGIN);
        assertSame(e, ErrorUtils.findApiException(new RuntimeException(e)));
    }

    @Test
    void findsApiExceptionThroughDamiWrapper() {
        ApiException e = ApiException.of(ErrorCode.NOT_LOGIN);
        assertSame(e, ErrorUtils.findApiException(new DamiLikeException(e)));
    }

    @Test
    void returnsNullWhenNotPresent() {
        assertNull(ErrorUtils.findApiException(null), "null 输入应返回 null");
        assertNull(ErrorUtils.findApiException(new RuntimeException("x")), "普通异常应返回 null");
    }

    @Test
    void selfReferencingCauseTerminates() {
        SelfCauseException selfLoop = new SelfCauseException();
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertNull(ErrorUtils.findApiException(selfLoop), "自环 cause 应返回 null 而非挂死"));
    }

    @Test
    void findCausePiercesTwoLayers() {
        IllegalArgumentException target = new IllegalArgumentException("boom");
        Throwable chain = new RuntimeException(new IllegalStateException(target));

        assertSame(target, ErrorUtils.findCause(chain, IllegalArgumentException.class));
        assertNull(ErrorUtils.findCause(chain, TimeoutException.class), "链上不存在时应返回 null");
    }

    @Test
    void detectsJsonMappingError() {
        assertTrue(ErrorUtils.isJsonMappingError(new JsonParseException(null, "x")),
                "JsonParseException 应被识别为 JSON 映射错误");
        assertFalse(ErrorUtils.isJsonMappingError(new RuntimeException("x")),
                "普通 RuntimeException 不应被识别为 JSON 映射错误");
    }
}
