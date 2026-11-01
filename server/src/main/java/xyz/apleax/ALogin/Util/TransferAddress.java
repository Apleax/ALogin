package xyz.apleax.ALogin.Util;

/**
 * 解析 minestom.transfer 目标地址（兼容 IPv6 字面量，如 [::1]:25565）
 *
 * @author Apleax
 */
public record TransferAddress(String host, int port) {
    private static final int DEFAULT_PORT = 25565;

    public static TransferAddress parse(String transfer) {
        if (transfer == null || transfer.isBlank())
            throw new IllegalArgumentException("minestom.transfer 未配置");
        String value = transfer.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("非法 minestom.transfer 配置（缺少 ]）: " + value);
            String host = value.substring(1, end);
            String rest = value.substring(end + 1);
            if (rest.isEmpty()) return new TransferAddress(host, DEFAULT_PORT);
            if (!rest.startsWith(":"))
                throw new IllegalArgumentException("非法 minestom.transfer 配置: " + value);
            return new TransferAddress(host, parsePort(rest.substring(1), value));
        }
        if (value.chars().filter(c -> c == ':').count() > 1)
            return new TransferAddress(value, DEFAULT_PORT);
        int idx = value.lastIndexOf(':');
        if (idx < 0) return new TransferAddress(value, DEFAULT_PORT);
        return new TransferAddress(value.substring(0, idx), parsePort(value.substring(idx + 1), value));
    }

    private static int parsePort(String portText, String raw) {
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("非法 minestom.transfer 端口: " + raw);
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法 minestom.transfer 配置: " + raw, e);
        }
    }
}
