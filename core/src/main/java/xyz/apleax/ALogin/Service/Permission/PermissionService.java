package xyz.apleax.ALogin.Service.Permission;

import cn.dev33.satoken.util.SaFoxUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.core.handle.Handler;
import org.noear.solon.core.route.Routing;
import xyz.apleax.ALogin.Config.PermissionProperties;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.PermissionNode;
import xyz.apleax.ALogin.Util.MailUtil;

import java.time.Duration;
import java.util.*;

/**
 * 权限鉴定服务：以 mcUuid 为身份键从 LuckPerms（权威源）读取节点并合并继承，提供接口/指令鉴权与受控批量写入
 * <p>
 * 一致性策略：本节点写入后立即失效本地缓存；外部（游戏内/后台）变更由 TTL 兜底。
 * 失败策略：鉴定失败按 {@code Permission.fail-closed} 处理，默认拒绝（fail-closed）。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class PermissionService implements LifecycleBean {
    /**
     * 继承展开的最大层数，防御异常数据造成的环
     */
    private static final int MAX_INHERITANCE_DEPTH = 8;
    /**
     * 权限组继承节点前缀（LuckPerms 中 group.<组名> 表示继承该组）
     */
    private static final String GROUP_NODE_PREFIX = "group.";
    private static final int DEFAULT_CACHE_TTL_SECONDS = 120;
    private static final int DEFAULT_GROUP_CACHE_TTL_SECONDS = 600;

    private final PermissionProperties properties;
    private final LuckPermsClient client;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull UUID, List<String>> permissionCache;
    private final LoadingCache<@NotNull UUID, List<String>> accountGroupCache;
    private final LoadingCache<@NotNull String, List<PermissionNode>> groupNodeCache;

    public PermissionService(@Inject("${Permission}") PermissionProperties properties,
                             LuckPermsClient client,
                             @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache) {
        this.properties = properties;
        this.client = client;
        this.accountCache = accountCache;
        this.permissionCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(ttl(properties.cacheTtlSeconds(), DEFAULT_CACHE_TTL_SECONDS)))
                .recordStats()
                .build(this::loadPermissions);
        this.accountGroupCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(ttl(properties.cacheTtlSeconds(), DEFAULT_CACHE_TTL_SECONDS)))
                .recordStats()
                .build(this::loadGroups);
        this.groupNodeCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofSeconds(ttl(properties.groupCacheTtlSeconds(), DEFAULT_GROUP_CACHE_TTL_SECONDS)))
                .build(this::loadGroupNodes);
    }

    /**
     * 启动自检：校验 permission.yml 的接口规则是否命中已注册路由
     * <p>
     * 配置驱动的鉴权有一个天然风险：路径写错或接口改名后规则不再命中，接口会"静默无鉴权"，
     * 因此启动后比对路由表并给出 WARN，同时打印实际生效的规则清单。
     * <p>
     * 注意：{@link LifecycleBean#start()} 阶段路由尚未注册完成，且控制器是逐个注册的，
     * 因此放入虚拟线程，等到"所有非通配规则都能命中"或超时后再给出结论，避免误报。
     */
    @Override
    public void start() {
        PermissionProperties.Api api = properties.api();
        if (api == null || !Boolean.TRUE.equals(api.enabled()) || api.rules() == null || api.rules().isEmpty()) return;
        Thread.ofVirtual().name("permission-rule-check").start(this::verifyApiRulesWhenReady);
    }

    /**
     * 等待路由就绪后执行自检；最多等待 10 秒
     */
    private void verifyApiRulesWhenReady() {
        Collection<Routing<Handler>> routings = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                routings = Solon.app().router().findAll();
            } catch (Exception e) {
                log.warn("接口权限规则自检跳过（读取路由表失败）: {}", e.getMessage());
                return;
            }
            if (routings != null && !routings.isEmpty() && countUnmatchedRules(routings) == 0) break;
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (routings == null || routings.isEmpty()) {
            log.info("接口权限规则自检跳过（启动后仍未读取到路由表）");
            return;
        }
        reportApiRules(routings);
    }

    /**
     * 统计当前路由表下"未命中"的规则数（通配路径不参与统计）
     */
    private int countUnmatchedRules(Collection<Routing<Handler>> routings) {
        int unmatched = 0;
        for (PermissionProperties.Api.Rule rule : properties.api().rules()) {
            if (rule == null || !Boolean.TRUE.equals(rule.enabled()) || isBlank(rule.path())) continue;
            if (rule.path().trim().contains("*")) continue;
            if (isBlank(rule.permission()) && isBlank(rule.group())) continue;
            if (matchRule(routings, rule) != 0) unmatched++;
        }
        return unmatched;
    }

    /**
     * 逐条输出规则生效情况；存在未命中规则时附带路由样例，便于直接定位路径写法问题
     */
    private void reportApiRules(Collection<Routing<Handler>> routings) {
        boolean hasUnmatched = false;
        for (PermissionProperties.Api.Rule rule : properties.api().rules()) {
            if (rule == null || !Boolean.TRUE.equals(rule.enabled()) || isBlank(rule.path())) continue;
            String path = rule.path().trim();
            if (isBlank(rule.permission()) && isBlank(rule.group())) {
                log.warn("接口权限规则既未配置 permission 也未配置 group，该规则不做任何校验: {}", path);
                continue;
            }
            if (path.contains("*")) {
                log.info("接口权限规则已加载（通配路径，跳过路由自检）: {} -> {}", path, describe(rule));
                continue;
            }
            switch (matchRule(routings, rule)) {
                case 1 -> {
                    hasUnmatched = true;
                    log.warn("接口权限规则未匹配到任何已注册路由，该规则不会生效、对应接口将无鉴权，请检查路径是否写错或接口已改名: {}", path);
                }
                case 2 -> {
                    hasUnmatched = true;
                    log.warn("接口权限规则路径匹配但 HTTP 方法不匹配，该规则不会生效: {} {}", rule.method(), path);
                }
                default ->
                        log.info("接口权限规则已生效: {} {} -> {}", isBlank(rule.method()) ? "ALL" : rule.method(), path, describe(rule));
            }
        }
        if (hasUnmatched) {
            List<String> paths = new ArrayList<>();
            for (Routing<Handler> routing : routings) {
                if (routing == null || isBlank(routing.path())) continue;
                paths.add(routing.method() == null ? routing.path() : routing.method().name + " " + routing.path());
                if (paths.size() >= 40) break;
            }
            log.warn("当前已注册路由样例: {}", paths);
        }
    }

    /**
     * 规则的鉴权要求可读描述（permission 与 group 可同时存在，二者都需通过）
     */
    private static String describe(PermissionProperties.Api.Rule rule) {
        List<String> parts = new ArrayList<>(2);
        if (!isBlank(rule.permission())) parts.add("permission=" + rule.permission());
        if (!isBlank(rule.group())) parts.add("group=" + rule.group());
        return String.join(", ", parts);
    }

    /**
     * 单条规则的匹配结果
     *
     * @return 0=命中；1=路径未命中；2=路径命中但方法不匹配
     */
    private int matchRule(Collection<Routing<Handler>> routings, PermissionProperties.Api.Rule rule) {
        String path = rule.path().trim();
        boolean pathMatched = false;
        for (Routing<Handler> routing : routings) {
            if (routing == null || !routing.test(path)) continue;
            pathMatched = true;
            String method = routing.method() == null ? null : routing.method().name;
            if (isBlank(rule.method()) || "ALL".equalsIgnoreCase(method) || rule.method().equalsIgnoreCase(method))
                return 0;
        }
        return pathMatched ? 2 : 1;
    }

    /**
     * 权限鉴定总开关
     */
    public boolean enabled() {
        return Boolean.TRUE.equals(properties.enabled());
    }

    /**
     * 取账号的权限节点集合（已展开继承，含通配节点）
     *
     * @param account 8 位账号（Sa-Token 的 loginId）
     */
    public List<String> permissions(String account) {
        UUID uniqueId = uniqueId(account);
        if (uniqueId == null) return List.of();
        return permissionsOf(uniqueId);
    }

    /**
     * 取账号所属的权限组集合（LuckPerms 组名，含继承得到的组；default 由 LuckPerms 隐式注入）
     */
    public List<String> groups(String account) {
        UUID uniqueId = uniqueId(account);
        if (uniqueId == null) return List.of();
        return groupsOf(uniqueId);
    }

    private List<String> groupsOf(UUID uniqueId) {
        try {
            return accountGroupCache.get(uniqueId);
        } catch (Exception e) {
            log.warn("读取权限组失败，uuid={}，按{}处理: {}", uniqueId, failClosed() ? "无权限组" : "放行", e.getMessage());
            return failClosed() ? List.of() : List.of("*");
        }
    }

    /**
     * 判定指定 mcUuid 是否属于某权限组（供非 Web 场景使用，如内置登录服指令）
     */
    public boolean hasGroup(UUID uniqueId, String group) {
        if (uniqueId == null || isBlank(group)) return false;
        return matches(groupsOf(uniqueId), group);
    }

    /**
     * 判定指定账号是否属于某权限组
     */
    public boolean hasGroup(String account, String group) {
        UUID uniqueId = uniqueId(account);
        return uniqueId != null && hasGroup(uniqueId, group);
    }

    /**
     * 判定指定 mcUuid 是否具备某权限节点（供非 Web 场景使用，如内置登录服指令）
     */
    public boolean hasPermission(UUID uniqueId, String permission) {
        if (uniqueId == null || permission == null || permission.isBlank()) return false;
        return matches(permissionsOf(uniqueId), permission);
    }

    /**
     * 判定指定账号是否具备某权限节点
     */
    public boolean hasPermission(String account, String permission) {
        UUID uniqueId = uniqueId(account);
        return uniqueId != null && hasPermission(uniqueId, permission);
    }

    /**
     * 匹配接口鉴权规则
     * <p>
     * 返回**所有**命中的规则：全部生效（逻辑与），避免"宽规则在前、严规则被静默忽略"的 fail-open 风险。
     *
     * @return 命中的规则列表；未命中返回空列表
     */
    public List<PermissionProperties.Api.Rule> matchApiRules(String path, String method) {
        PermissionProperties.Api api = properties.api();
        if (api == null || !Boolean.TRUE.equals(api.enabled()) || api.rules() == null) return List.of();
        if (path == null) return List.of();
        List<PermissionProperties.Api.Rule> matched = new ArrayList<>();
        for (PermissionProperties.Api.Rule rule : api.rules()) {
            if (rule == null || !Boolean.TRUE.equals(rule.enabled())) continue;
            if (isBlank(rule.path()) || (isBlank(rule.permission()) && isBlank(rule.group()))) continue;
            if (!isBlank(rule.method()) && method != null && !rule.method().equalsIgnoreCase(method)) continue;
            if (SaFoxUtil.vagueMatch(rule.path(), path)) matched.add(rule);
        }
        return matched;
    }

    /**
     * 匹配内置登录服指令鉴权规则
     * <p>
     * 返回**所有**命中的规则（指令名或别名命中即算），全部生效（逻辑与）。
     *
     * @param commandNames 指令名与别名（不含前导斜杠）
     * @return 命中的规则列表；未命中返回空列表
     */
    public List<PermissionProperties.Command.Rule> matchCommandRules(String... commandNames) {
        PermissionProperties.Command command = properties.command();
        if (command == null || !Boolean.TRUE.equals(command.enabled()) || command.rules() == null) return List.of();
        if (commandNames == null) return List.of();
        List<PermissionProperties.Command.Rule> matched = new ArrayList<>();
        for (String commandName : commandNames) {
            if (isBlank(commandName)) continue;
            for (PermissionProperties.Command.Rule rule : command.rules()) {
                if (rule == null || !Boolean.TRUE.equals(rule.enabled())) continue;
                if (isBlank(rule.name()) || (isBlank(rule.permission()) && isBlank(rule.group()))) continue;
                if (rule.name().equalsIgnoreCase(commandName.trim()) && !matched.contains(rule)) matched.add(rule);
            }
        }
        return matched;
    }

    /**
     * 受控批量写入 LuckPerms 权限节点（写前需具备编辑权限，由调用方通过 {@link #writePermission()} 校验）
     * <p>
     * 批量语义：新增与删除各一次请求，写入后失效本地缓存并触发游戏服同步，最后发送高敏操作通知邮件。
     *
     * @param operator 操作者账号（用于审计与通知）
     * @param account  目标账号
     * @param add      新增节点
     * @param remove   删除节点
     * @return 可读的变更摘要
     */
    public String applyNodes(String operator, String account, List<PermissionNode> add, List<PermissionNode> remove) {
        if (!Boolean.TRUE.equals(properties.write() == null ? null : properties.write().enabled()))
            throw new IllegalStateException("权限写入未开启");
        UUID uniqueId = uniqueId(account);
        if (uniqueId == null) throw new IllegalStateException("目标账号不存在或未绑定 mcUuid: " + account);

        try {
            client.addNodes(uniqueId, add);
            client.removeNodes(uniqueId, remove);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("权限写入被中断: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("权限写入失败: " + e.getMessage(), e);
        }

        invalidate(account);
        try {
            client.pushUpdate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("触发游戏服权限同步失败（写入已生效），uuid={}: {}", uniqueId, e.getMessage());
        }

        String summary = buildSummary(add, remove);
        notifyChange(operator, account, summary);
        log.info("LuckPerms 权限变更完成，操作者={}，目标={}，变更={}", operator, account, summary.replace('\n', ' '));
        return summary;
    }

    /**
     * 受控写入所需的前置权限节点
     */
    public String writePermission() {
        PermissionProperties.Write write = properties.write();
        return write == null ? null : write.requirePermission();
    }

    /**
     * 受控写入是否开启
     */
    public boolean writeEnabled() {
        PermissionProperties.Write write = properties.write();
        return write != null && Boolean.TRUE.equals(write.enabled());
    }

    /**
     * 失效指定账号的权限与权限组缓存（账号维度）
     */
    public void invalidate(String account) {
        UUID uniqueId = uniqueId(account);
        if (uniqueId == null) return;
        permissionCache.invalidate(uniqueId);
        accountGroupCache.invalidate(uniqueId);
    }

    /**
     * 失效全部权限相关缓存
     */
    public void invalidateAll() {
        permissionCache.invalidateAll();
        accountGroupCache.invalidateAll();
        groupNodeCache.invalidateAll();
    }

    private List<String> permissionsOf(UUID uniqueId) {
        try {
            return permissionCache.get(uniqueId);
        } catch (Exception e) {
            log.warn("权限鉴定失败，uuid={}，按{}处理: {}", uniqueId, failClosed() ? "无权限(fail-closed)" : "放行(fail-open)", e.getMessage());
            return failClosed() ? List.of() : List.of("*");
        }
    }

    private boolean failClosed() {
        return !Boolean.FALSE.equals(properties.failClosed());
    }

    private List<String> loadPermissions(UUID uniqueId) {
        List<String> permissions = new ArrayList<>();
        for (PermissionNode node : collectNodes(uniqueId)) {
            if (!node.effective()) continue;
            String type = node.type();
            if ("permission".equals(type) || "regex_permission".equals(type)) permissions.add(node.getKey());
        }
        return List.copyOf(permissions);
    }

    /**
     * 计算账号所属的权限组：从继承闭包中收集所有生效的 {@code group.<组名>} 继承节点
     * <p>
     * {@link #collectNodes(UUID)} 已从"用户的直接组"出发按层展开，因此闭包里出现的每个继承节点
     * 都代表用户确实可达（直接所属或经其它组继承而来），语义与 LuckPerms 的"继承组"一致。
     */
    private List<String> loadGroups(UUID uniqueId) {
        Set<String> groups = new LinkedHashSet<>();
        for (PermissionNode node : collectNodes(uniqueId)) {
            if (!"inheritance".equals(node.type()) || !node.effective()) continue;
            String key = node.getKey();
            if (key != null && key.length() > GROUP_NODE_PREFIX.length())
                groups.add(key.substring(GROUP_NODE_PREFIX.length()));
        }
        return List.copyOf(groups);
    }

    private List<PermissionNode> loadGroupNodes(String groupName) {
        try {
            return client.groupNodes(groupName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取组节点被中断: " + groupName, e);
        } catch (Exception e) {
            throw new IllegalStateException("读取组节点失败: " + groupName + " - " + e.getMessage(), e);
        }
    }

    /**
     * 收集用户节点并展开继承（含多层继承与环保护）
     */
    private List<PermissionNode> collectNodes(UUID uniqueId) {
        List<PermissionNode> nodes;
        try {
            nodes = client.userNodes(uniqueId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取用户权限节点被中断: " + uniqueId, e);
        } catch (Exception e) {
            throw new IllegalStateException("读取用户权限节点失败: " + uniqueId + " - " + e.getMessage(), e);
        }

        List<PermissionNode> all = new ArrayList<>(nodes);
        Deque<String> pending = new ArrayDeque<>();
        enqueueGroups(nodes, pending);
        Set<String> visited = new HashSet<>();
        int depth = 0;
        while (!pending.isEmpty() && depth++ < MAX_INHERITANCE_DEPTH) {
            int size = pending.size();
            for (int i = 0; i < size; i++) {
                String group = pending.poll();
                if (group == null || !visited.add(group)) continue;
                List<PermissionNode> groupNodes = groupNodeCache.get(group);
                all.addAll(groupNodes);
                enqueueGroups(groupNodes, pending);
            }
        }
        return all;
    }

    private static void enqueueGroups(List<PermissionNode> nodes, Deque<String> pending) {
        for (PermissionNode node : nodes) {
            if (!"inheritance".equals(node.type()) || !node.effective()) continue;
            String key = node.getKey();
            if (key != null && key.length() > "group.".length()) pending.add(key.substring("group.".length()));
        }
    }

    private UUID uniqueId(String account) {
        if (account == null || account.isBlank()) return null;
        try {
            AccountPO accountPO = accountCache.get(account);
            if (accountPO == null || accountPO.getMcUuid() == null) {
                log.debug("账号未绑定 mcUuid，无法进行权限鉴定: {}", account);
                return null;
            }
            return accountPO.getMcUuid();
        } catch (Exception e) {
            log.warn("读取账号失败，account={}: {}", account, e.getMessage());
            return null;
        }
    }


    private void notifyChange(String operator, String account, String summary) {
        List<String> emails = properties.notifyEmails();
        if (emails == null || emails.isEmpty()) return;
        try {
            MailUtil.sendPermissionChangeNotifyAsync(emails, operator, account, summary);
        } catch (Exception e) {
            log.warn("权限变更通知邮件发送失败，目标={}: {}", account, e.getMessage());
        }
    }

    private static String buildSummary(List<PermissionNode> add, List<PermissionNode> remove) {
        StringBuilder builder = new StringBuilder();
        if (add != null && !add.isEmpty()) {
            builder.append("新增 ").append(add.size()).append(" 项:");
            for (PermissionNode node : add) builder.append("\n+ ").append(node.getKey());
        }
        if (remove != null && !remove.isEmpty()) {
            if (!builder.isEmpty()) builder.append('\n');
            builder.append("删除 ").append(remove.size()).append(" 项:");
            for (PermissionNode node : remove) builder.append("\n- ").append(node.getKey());
        }
        return builder.isEmpty() ? "无变更" : builder.toString();
    }

    /**
     * 权限匹配：支持精确、{@code *} 与 {@code 前缀.*} 通配（与 Sa-Token 的判定保持一致）
     */
    private static boolean matches(List<String> permissions, String permission) {
        for (String granted : permissions) {
            if (granted == null) continue;
            if (granted.equals(permission) || "*".equals(granted)) return true;
            if (granted.endsWith(".*") && permission.startsWith(granted.substring(0, granted.length() - 1)))
                return true;
        }
        return false;
    }

    private static int ttl(Integer configured, int defaultValue) {
        return configured == null || configured <= 0 ? defaultValue : configured;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
