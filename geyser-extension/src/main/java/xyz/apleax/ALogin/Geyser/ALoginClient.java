package xyz.apleax.ALogin.Geyser;

import com.google.gson.*;
import org.geysermc.geyser.api.extension.ExtensionLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 调用 ALogin 的 HTTP 接口: 密码登录换取 sa-token, 以及基岩版 XUID 的免密登录与绑定
 *
 * @author Apleax
 */
final class ALoginClient {
    private static final Gson GSON = new Gson();
    /**
     * 所有玩家共用的连接池（固定 HTTP/1.1）
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String baseUrl;
    private final Settings.Header extraHeader;
    private final ExtensionLogger logger;

    ALoginClient(String baseUrl, Settings.Header extraHeader, ExtensionLogger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.extraHeader = extraHeader;
        this.logger = logger;
    }

    /**
     * 用邮箱密码登录, 返回的 token 供进服 cookie 使用
     */
    Result login(String email, String password) {
        return login("/api/web/account/Login/", GSON.toJson(Map.of(
                "type", "Email",
                "email", email,
                "password", password)), "登录");
    }

    /**
     * 已绑定 XUID 的免密登录
     */
    Result autoLoginByXuid(String xuid, String name) {
        return login("/api/internal/bedrock/AutoLogin", GSON.toJson(Map.of(
                "xuid", xuid,
                "name", name == null ? "" : name)), "免密登录");
    }

    /**
     * 把 XUID 绑定到 token 对应的账号（失败只记日志）
     */
    boolean bindXuid(String token, String xuid, String name) {
        JsonObject body;
        try {
            body = post("/api/internal/bedrock/Bind", GSON.toJson(Map.of(
                    "token", token,
                    "xuid", xuid,
                    "name", name == null ? "" : name)));
        } catch (Exception e) {
            logger.warning("调用 ALogin 绑定接口失败(" + xuid + "): " + e.getMessage());
            return false;
        }
        if (body == null) {
            logger.warning("绑定基岩版 XUID 失败(" + xuid + "): 认证服务返回异常");
            return false;
        }
        if (isOk(body)) {
            logger.info("已绑定基岩版 XUID: " + xuid + " -> " + name);
            return true;
        }
        logger.warning("绑定基岩版 XUID 失败(" + xuid + "): " + message(body, "未知原因"));
        return false;
    }

    /**
     * 调用 ALogin 并取出 data 作为 token(登录/免密登录共用)
     */
    private Result login(String path, String jsonBody, String action) {
        JsonObject body;
        try {
            body = post(path, jsonBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(null, action + "请求被中断, 请重试");
        } catch (Exception e) {
            logger.error("Failed to " + path + " through ALogin: " + e.getMessage());
            return new Result(null, "无法连接认证服务, 请稍后再试");
        }
        if (body == null) return new Result(null, "认证服务返回异常, 请联系管理员");
        JsonElement data = isOk(body) ? body.get("data") : null;
        if (data != null) {
            String token = null;
            if (data.isJsonObject() && data.getAsJsonObject().has("tokenValue"))
                token = data.getAsJsonObject().get("tokenValue").getAsString();
            else if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString())
                token = data.getAsString();
            if (token != null && !token.isBlank()) return new Result(token, null);
        }
        return new Result(null, message(body, action + "失败"));
    }

    /**
     * 发起一次 JSON POST 并解析响应
     *
     * @throws Exception 传输层异常（网络/中断）
     */
    private JsonObject post(String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json");
        if (extraHeader != null) builder.header(extraHeader.name(), extraHeader.value());
        HttpResponse<String> response = HTTP_CLIENT.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject body = parseObject(response.body());
        if (body == null) {
            logger.error("ALogin returned a non-JSON response (HTTP " + response.statusCode() + ") from "
                    + baseUrl + path + ": " + snippet(response.body()));
        }
        return body;
    }

    private static boolean isOk(JsonObject body) {
        return body.has("code") && body.get("code").getAsInt() == 200;
    }

    private static String message(JsonObject body, String fallback) {
        return body.has("description") && !body.get("description").getAsString().isBlank()
                ? body.get("description").getAsString() : fallback;
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static String snippet(String body) {
        String text = body.strip();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    record Result(String token, String message) {
        boolean success() {
            return token != null;
        }
    }
}
