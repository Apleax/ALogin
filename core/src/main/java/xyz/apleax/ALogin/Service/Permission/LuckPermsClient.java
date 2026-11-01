package xyz.apleax.ALogin.Service.Permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Config.PermissionProperties;
import xyz.apleax.ALogin.POJO.PermissionNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LuckPerms REST API 客户端（只读查询 + 受控批量写入）
 * <p>
 * 接口语义依据实测：节点写入仅接受 key/value/context/expiry（带 type 会被拒绝）；
 * 批量新增使用 PATCH 数组，批量删除使用带数组 body 的 DELETE。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class LuckPermsClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2_000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 3_000;

    private final PermissionProperties properties;
    private final HttpClient httpClient;

    public LuckPermsClient(@Inject("${Permission}") PermissionProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout(properties.connectTimeoutMs(), DEFAULT_CONNECT_TIMEOUT_MS)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 读取用户权限节点（原始节点，含继承节点）
     */
    public List<PermissionNode> userNodes(UUID uniqueId) throws IOException, InterruptedException {
        HttpResponse<String> response = send(request("/user/" + uniqueId + "/nodes").GET().build());
        if (response.statusCode() == 404) return List.of();
        requireSuccess(response, "读取用户权限节点失败");
        return parseNodes(response.body());
    }

    /**
     * 读取组权限节点
     */
    public List<PermissionNode> groupNodes(String groupName) throws IOException, InterruptedException {
        HttpResponse<String> response = send(request("/group/" + groupName + "/nodes").GET().build());
        if (response.statusCode() == 404) return List.of();
        requireSuccess(response, "读取组权限节点失败");
        return parseNodes(response.body());
    }

    /**
     * 批量新增权限节点（单次请求）
     */
    public void addNodes(UUID uniqueId, List<PermissionNode> nodes) throws IOException, InterruptedException {
        if (nodes == null || nodes.isEmpty()) return;
        HttpResponse<String> response = send(request("/user/" + uniqueId + "/nodes")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(writeNodes(nodes), StandardCharsets.UTF_8))
                .build());
        requireSuccess(response, "批量新增权限节点失败");
    }

    /**
     * 批量删除权限节点（单次请求）
     */
    public void removeNodes(UUID uniqueId, List<PermissionNode> nodes) throws IOException, InterruptedException {
        if (nodes == null || nodes.isEmpty()) return;
        HttpResponse<String> response = send(request("/user/" + uniqueId + "/nodes")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(writeNodes(nodes), StandardCharsets.UTF_8))
                .build());
        requireSuccess(response, "批量删除权限节点失败");
    }

    /**
     * 触发游戏服侧同步（LuckPerms messaging）
     */
    public void pushUpdate() throws IOException, InterruptedException {
        HttpResponse<String> response = send(request("/messaging/update")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
        requireSuccess(response, "触发权限同步失败");
    }

    /**
     * 服务可用性探测（不抛异常，供降级判断与日志使用）
     */
    public boolean healthy() {
        try {
            HttpResponse<String> response = send(request("/health").GET().build());
            if (response.statusCode() != 200) return false;
            return MAPPER.readTree(response.body()).path("healthy").asBoolean(false);
        } catch (Exception e) {
            log.warn("LuckPerms 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private HttpRequest.Builder request(String path) {
        String baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().trim();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeout(properties.requestTimeoutMs(), DEFAULT_REQUEST_TIMEOUT_MS)))
                .header("Accept", "application/json");
        String apiKey = properties.apiKey();
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.trim());
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void requireSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return;
        throw new IllegalStateException(action + "，HTTP " + response.statusCode() + ": " + brief(response.body()));
    }

    private static String brief(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private static int timeout(Integer configured, int defaultValue) {
        return configured == null || configured <= 0 ? defaultValue : configured;
    }

    /**
     * 序列化节点：仅输出 LuckPerms 接受的字段（key/value/context/expiry）
     */
    private static String writeNodes(List<PermissionNode> nodes) {
        ArrayNode array = MAPPER.createArrayNode();
        for (PermissionNode node : nodes) {
            if (node == null || node.getKey() == null || node.getKey().isBlank()) continue;
            ObjectNode json = array.addObject();
            json.put("key", node.getKey());
            json.put("value", !Boolean.FALSE.equals(node.getValue()));
            if (node.getExpiry() != null) json.put("expiry", node.getExpiry());
            Map<String, String> context = node.getContext();
            if (context != null && !context.isEmpty()) {
                ArrayNode contextArray = json.putArray("context");
                context.forEach((key, value) -> {
                    if (key == null || value == null) return;
                    ObjectNode pair = contextArray.addObject();
                    pair.put("key", key.toLowerCase());
                    pair.put("value", value.toLowerCase());
                });
            }
        }
        try {
            return MAPPER.writeValueAsString(array);
        } catch (Exception e) {
            throw new IllegalStateException("序列化权限节点失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 LuckPerms 返回的节点数组（context 数组 → Map）
     */
    private static List<PermissionNode> parseNodes(String body) throws IOException {
        List<PermissionNode> nodes = new ArrayList<>();
        if (body == null || body.isBlank()) return nodes;
        JsonNode root = MAPPER.readTree(body);
        if (!root.isArray()) return nodes;
        for (JsonNode element : root) {
            String key = element.path("key").asText(null);
            if (key == null || key.isBlank()) continue;
            PermissionNode node = new PermissionNode(key);
            node.setValue(element.path("value").asBoolean(true));
            if (element.hasNonNull("expiry")) node.setExpiry(element.path("expiry").asLong());
            JsonNode context = element.path("context");
            if (context.isArray() && !context.isEmpty()) {
                Map<String, String> pairs = new LinkedHashMap<>();
                for (JsonNode pair : context) {
                    String contextKey = pair.path("key").asText(null);
                    String contextValue = pair.path("value").asText(null);
                    if (contextKey != null && contextValue != null) pairs.put(contextKey, contextValue);
                }
                if (!pairs.isEmpty()) node.setContext(pairs);
            }
            nodes.add(node);
        }
        return nodes;
    }
}
