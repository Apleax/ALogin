package xyz.apleax.ALogin.Error;

/**
 * 携带错误码的业务异常。
 */
public final class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    /**
     * 已格式化后的最终文案
     */
    private final String description;
    /**
     * 可为 null；限流剩余秒数等
     */
    private final Object data;

    private ApiException(ErrorCode code, String description, Object data, Throwable cause) {
        super(description, cause);
        this.errorCode = code;
        this.description = description;
        this.data = data;
    }

    /**
     * 使用错误码默认文案
     */
    public static ApiException of(ErrorCode code) {
        return new ApiException(code, code.message(), null, null);
    }

    /**
     * 按文案模板格式化参数
     */
    public static ApiException of(ErrorCode code, Object... args) {
        return new ApiException(code, format(code, args), null, null);
    }

    /**
     * 携带底层原因
     */
    public static ApiException causedBy(ErrorCode code, Throwable cause, Object... args) {
        return new ApiException(code, format(code, args), null, cause);
    }

    /**
     * 携带附带数据
     */
    public static ApiException withData(ErrorCode code, Object data) {
        return new ApiException(code, code.message(), data, null);
    }

    /**
     * 外部文案原样使用，不做格式化
     */
    public static ApiException raw(ErrorCode code, String description) {
        return new ApiException(code, description, null, null);
    }

    /**
     * 仅当有参数时格式化模板
     */
    private static String format(ErrorCode code, Object... args) {
        return args == null || args.length == 0 ? code.message() : String.format(code.message(), args);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String description() {
        return description;
    }

    public Object data() {
        return data;
    }
}
