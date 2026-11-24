package xyz.apleax.ALogin.Util;

import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;

/**
 * Result 的统一构造入口，全项目只允许在这里调用 Result.succeed / failure。
 */
public final class ApiResults {
    private ApiResults() {
    }

    /**
     * 成功并携带数据
     */
    public static <T> Result<T> ok(T data) {
        return Result.succeed(data);
    }

    /**
     * 成功并自定义文案
     */
    public static <T> Result<T> ok(T data, String description) {
        return Result.succeed(data, description);
    }

    /**
     * 无数据成功
     */
    public static Result<Void> ok() {
        return Result.succeed();
    }

    /**
     * 由异常构造失败结果
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> failure(ApiException e) {
        return (Result<T>) Result.failure(e.errorCode().code(), e.description(), e.data());
    }

    /**
     * 由错误码构造失败结果
     */
    public static <T> Result<T> failure(ErrorCode code) {
        return Result.failure(code.code(), code.message());
    }
}
