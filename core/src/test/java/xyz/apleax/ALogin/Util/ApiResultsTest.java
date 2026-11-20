package xyz.apleax.ALogin.Util;

import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Result 统一构造入口：成功码/文案/数据映射，以及前端限流倒计时所依赖的 data 透传契约
 */
class ApiResultsTest {

    @Test
    void okWithData() {
        Result<String> result = ApiResults.ok("x");

        assertEquals(200, result.getCode());
        assertEquals("x", result.getData());
        assertTrue(isBlank(result.getDescription()),
                "无自定义文案时 description 应为空白，实际：" + result.getDescription());
    }

    @Test
    void okWithDataAndDescription() {
        Result<Boolean> result = ApiResults.ok(true, "提示");

        assertEquals(200, result.getCode());
        assertEquals(Boolean.TRUE, result.getData());
        assertEquals("提示", result.getDescription());
    }

    @Test
    void okWithoutData() {
        Result<Void> result = ApiResults.ok();

        assertEquals(200, result.getCode());
        assertNull(result.getData());
        assertTrue(isBlank(result.getDescription()),
                "无自定义文案时 description 应为空白，实际：" + result.getDescription());
    }

    /**
     * 前端限流倒计时契约：失败结果必须把 data 里的剩余秒数一起带出去
     */
    @Test
    void failureFromExceptionKeepsCodeDescriptionAndData() {
        Result<Object> result =
                ApiResults.failure(ApiException.withData(ErrorCode.VERIFY_CODE_FREQUENT, 42L));

        assertEquals(429001, result.getCode());
        assertEquals("获取失败", result.getDescription());
        assertEquals(42L, result.getData());
    }

    @Test
    void failureFromErrorCodeHasNoData() {
        Result<Object> result = ApiResults.failure(ErrorCode.ACCOUNT_NOT_FOUND);

        assertEquals(404002, result.getCode());
        assertEquals("账号不存在", result.getDescription());
        assertNull(result.getData());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
