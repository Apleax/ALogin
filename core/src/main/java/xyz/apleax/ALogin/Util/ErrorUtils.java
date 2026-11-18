package xyz.apleax.ALogin.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import xyz.apleax.ALogin.Error.ApiException;

/**
 * 异常链工具。
 */
public final class ErrorUtils {
    /**
     * 最大穿透深度
     */
    private static final int MAX_DEPTH = 16;

    private ErrorUtils() {
    }

    /**
     * 沿 cause 链找 ApiException，找不到返回 null；穿透 Dami LPC 的包装。
     */
    public static ApiException findApiException(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof ApiException e) return e;
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return null;
    }

    /**
     * 沿 cause 链找指定类型异常，找不到返回 null。
     */
    public static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable current = t;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (type.isInstance(current)) return type.cast(current);
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return null;
    }

    /**
     * cause 链里是否有 Jackson 解析/类型错误。
     */
    public static boolean isJsonMappingError(Throwable t) {
        Throwable current = t;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof JsonProcessingException) return true;
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return false;
    }
}
