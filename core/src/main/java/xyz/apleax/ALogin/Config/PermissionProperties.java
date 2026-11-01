package xyz.apleax.ALogin.Config;

import java.util.List;

/**
 * LuckPerms 权限集成配置（对应 {@code ALogin/permission.yml} 的 Permission 节点）
 * <p>
 * 独立于 config.yml，便于权限相关配置单独维护；所有开关均为"不配置即不启用"的保守默认值。
 *
 * @author Apleax
 */
public record PermissionProperties(
        /**
         * 权限鉴定总开关：关闭时不加载规则、不做任何权限校验（行为与集成前一致）
         */
        Boolean enabled,
        /**
         * LuckPerms REST API 地址（局域网内可直接使用容器名，如 http://luckperms:8080）
         */
        String baseUrl,
        /**
         * REST API 访问密钥，对应服务端 LUCKPERMS_REST_AUTH_KEYS 中的一项；为空则不带 Authorization 头
         */
        String apiKey,
        /**
         * 连接超时（毫秒）
         */
        Integer connectTimeoutMs,
        /**
         * 请求超时（毫秒）
         */
        Integer requestTimeoutMs,
        /**
         * 权限缓存有效期（秒）：包含"权限节点集合"与"所属权限组"，自身写入后立即失效，此处兜底外部变更
         */
        Integer cacheTtlSeconds,
        /**
         * 权限组的权限节点缓存有效期（秒），继承展开时使用，组数据变更频率低
         */
        Integer groupCacheTtlSeconds,
        /**
         * 权限鉴定失败（超时/不可达/非预期响应）时是否按"无权限"处理：true=拒绝（fail-closed）
         */
        Boolean failClosed,
        /**
         * 接口鉴权规则
         */
        Api api,
        /**
         * 内置登录服指令鉴权规则
         */
        Command command,
        /**
         * 高敏操作（修改 LuckPerms 权限）通知邮箱列表
         */
        List<String> notifyEmails,
        /**
         * 受控写入 LuckPerms 的开关与前置权限节点
         */
        Write write) {

    /**
     * 接口鉴权规则集
     */
    public record Api(Boolean enabled, List<Rule> rules) {
        /**
         * @param enabled    该规则是否启用
         * @param path       请求路径，支持 {@code *} 通配（如 /api/web/permission/*）
         * @param method     HTTP 方法，留空表示匹配所有方法
         * @param permission 需要的权限节点（与 group 可同时配置，二者都需通过）
         * @param group      需要的权限组（LuckPerms 组名，如 admins，含继承得到的组）
         */
        public record Rule(Boolean enabled, String path, String method, String permission, String group) {
        }
    }

    /**
     * 内置登录服指令鉴权规则集
     */
    public record Command(Boolean enabled, List<Rule> rules) {
        /**
         * @param enabled    该规则是否启用
         * @param name       指令名（不含前导斜杠，如 login 或别名 l）
         * @param permission 需要的权限节点（与 group 可同时配置，二者都需通过）
         * @param group      需要的权限组（LuckPerms 组名，如 admins，含继承得到的组）
         */
        public record Rule(Boolean enabled, String name, String permission, String group) {
        }
    }

    /**
     * 受控写入
     */
    public record Write(Boolean enabled, String requirePermission) {
    }
}
