package xyz.apleax.ALogin.Util;

/**
 * 客户端 IP 判据：所有登录入口（网页/游戏指令/正版断言）共用同一份规则，
 * 避免同一段校验在多处复制后逐渐走样（曾出现"登录入口校验、断言入口只归一化"的偏差）。
 *
 * @author Apleax
 */
public final class ClientIp {
    /**
     * 纯 IP 文本的长度上限（IPv6 字面量最长 45 字符）
     */
    public static final int MAX_LENGTH = 45;

    private ClientIp() {
    }

    /**
     * 是否为可直接落库的纯 IP：非空、长度合法、不含端口/路径分隔符与空白
     */
    public static boolean isValid(String ip) {
        return ip != null && !ip.isBlank() && ip.length() <= MAX_LENGTH
                && ip.indexOf('/') < 0 && ip.chars().noneMatch(Character::isWhitespace);
    }

    /**
     * 非法值归一为 null（"IP 可空"的场景使用，例如断言里插件取不到真 IP 时）
     */
    public static String normalize(String ip) {
        return isValid(ip) ? ip : null;
    }
}
