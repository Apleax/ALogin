package xyz.apleax.ALogin.POJO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * LuckPerms 权限节点（对齐 LuckPerms 的 node 规范：key + value + context + expiry）
 * <p>
 * 说明：LuckPerms 的节点是扁平的 key（如 {@code alogin.account.cache.refresh}），节点的"类型"由 key 前缀推导，
 * 不再使用树形结构；上下文与过期时间与 LuckPerms 语义保持一致。
 *
 * @author Apleax
 */
@Getter
@Setter
@NoArgsConstructor
public class PermissionNode {

    /**
     * 节点 key
     */
    private String key;
    /**
     * 节点值：true=授予，false=显式拒绝；为 null 时按 true 处理
     */
    private Boolean value;
    /**
     * 上下文键值对（键与 LuckPerms 一致，需为小写）；可为空
     */
    private Map<String, String> context;
    /**
     * 过期时间（epoch 秒）；为 null 表示永久有效
     */
    private Long expiry;

    public PermissionNode(String key) {
        this.key = key;
    }

    public PermissionNode(String key, Boolean value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 节点当前是否生效：未被显式拒绝且未过期
     */
    public boolean effective() {
        return !Boolean.FALSE.equals(value) && !expired();
    }

    /**
     * 节点是否已过期
     */
    public boolean expired() {
        return expiry != null && expiry <= Instant.now().getEpochSecond();
    }

    /**
     * 按 key 推导节点类型，规则与 LuckPerms 一致
     *
     * @return permission / regex_permission / inheritance / prefix / suffix / meta / weight / display_name
     */
    public String type() {
        if (key == null || key.isEmpty()) return "permission";
        if (key.startsWith("r=")) return "regex_permission";
        if (key.startsWith("group.")) return "inheritance";
        if (key.startsWith("prefix.")) return "prefix";
        if (key.startsWith("suffix.")) return "suffix";
        if (key.startsWith("meta.")) return "meta";
        if (key.startsWith("weight.")) return "weight";
        if (key.startsWith("displayname.")) return "display_name";
        return "permission";
    }
}
