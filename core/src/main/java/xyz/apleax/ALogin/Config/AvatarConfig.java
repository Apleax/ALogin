package xyz.apleax.ALogin.Config;

import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;

/**
 * 玩家头像兜底配置（对应 {@code ALogin/config.yml} 的 {@code Avatar} 节点）。
 * <p>
 * 用静态 {@code @Inject} 字段（与 {@code PremiumAssertionService}、{@code MailUtil} 同款）而不是 record 绑定：
 * 外置 config.yml 不会自动补新键，缺节点时必须按"未配置"处理而不是让启动失败。
 *
 * @author Apleax
 */
@Managed
public class AvatarConfig {
    /**
     * 默认头像来源：{@code http(s)://} URL，或相对 {@code ./ALogin/} 的文件路径（如 {@code icons/avatar-default.png}）
     */
    @Inject("${Avatar.default-source:}")
    private static String defaultSource;
    /**
     * 远程默认头像的下载超时（毫秒）
     */
    @Inject("${Avatar.fetch-timeout-ms:3000}")
    private static int fetchTimeoutMs;
    /**
     * 头像响应的浏览器缓存秒数
     */
    @Inject("${Avatar.cache-seconds:600}")
    private static int cacheSeconds;

    /**
     * 默认头像来源，去空白；未配置返回空串
     */
    public static String defaultSource() {
        return defaultSource == null ? "" : defaultSource.trim();
    }

    /**
     * 下载超时，非正值回退 3000ms
     */
    public static int fetchTimeoutMs() {
        return fetchTimeoutMs > 0 ? fetchTimeoutMs : 3000;
    }

    /**
     * 缓存秒数，负值回退 600s
     */
    public static int cacheSeconds() {
        return cacheSeconds >= 0 ? cacheSeconds : 600;
    }
}
