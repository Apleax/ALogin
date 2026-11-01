package xyz.apleax.ALogin.Service.Premium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import xyz.apleax.ALogin.POJO.PremiumProfile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 微软 OAuth 正版验证服务
 *
 * @author Apleax
 * @see <a href="https://wiki.vg/Microsoft_Authentication_Scheme">Microsoft Authentication Scheme</a>
 */
@Slf4j
@Managed
public class PremiumOAuthService {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_LAUNCHER_LOGIN_URL = "https://api.minecraftservices.com/launcher/login";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String USER_AGENT = "ALogin";
    private static final String XBL_CONTRACT_VERSION = "1";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 设备码申请结果（deviceCode 不下发前端）
     */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri,
                             long intervalSeconds, long expiresInSeconds) {
    }

    /**
     * 设备码轮询结果
     *
     * @param status         轮询状态
     * @param microsoftToken 成功时的微软 access_token
     * @param userMessage    失败原因
     */
    public record DevicePoll(Status status, String microsoftToken, String userMessage) {
        public enum Status {PENDING, SLOW_DOWN, SUCCESS, FAILED}
    }

    /**
     * 申请设备码
     */
    public Result<DeviceCode> startDeviceCode() {
        String form = "client_id=" + encode(CLIENT_ID)
                + "&scope=" + encode(SCOPE)
                + "&response_type=device_code";
        try {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build());
            JsonNode body = parseBody(response);
            if (response.statusCode() != 200) {
                log.warn("申请微软设备码失败：status={}, error={}",
                        response.statusCode(), body.path("error").asText(""));
                return Result.failure("微软授权服务不可用，请稍后再试");
            }
            String deviceCode = body.path("device_code").asText("");
            String userCode = body.path("user_code").asText("");
            if (deviceCode.isEmpty() || userCode.isEmpty()) {
                log.warn("申请微软设备码响应异常：{}", truncate(response.body()));
                return Result.failure("微软授权服务响应异常，请稍后再试");
            }
            return Result.succeed(new DeviceCode(deviceCode, userCode,
                    body.path("verification_uri").asText("https://www.microsoft.com/link"),
                    Math.max(1, body.path("interval").asLong(5)),
                    Math.max(60, body.path("expires_in").asLong(900))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure("请求被中断，请重试");
        } catch (Exception e) {
            log.warn("申请微软设备码异常：{}", e.getMessage());
            return Result.failure("微软授权服务连接失败，请稍后再试");
        }
    }

    /**
     * 轮询一次设备码授权结果
     */
    public DevicePoll pollDeviceCode(String deviceCode) {
        String form = "client_id=" + encode(CLIENT_ID)
                + "&grant_type=" + encode(DEVICE_CODE_GRANT_TYPE)
                + "&device_code=" + encode(deviceCode);
        try {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build());
            JsonNode body = parseBody(response);
            if (response.statusCode() == 200) {
                String token = body.path("access_token").asText("");
                if (!token.isEmpty()) return new DevicePoll(DevicePoll.Status.SUCCESS, token, null);
                log.warn("微软设备码轮询响应缺少 access_token：{}", truncate(response.body()));
                return new DevicePoll(DevicePoll.Status.FAILED, null, "微软授权响应异常，请重试");
            }
            String error = body.path("error").asText("");
            return switch (error) {
                case "authorization_pending" -> new DevicePoll(DevicePoll.Status.PENDING, null, null);
                case "slow_down" -> new DevicePoll(DevicePoll.Status.SLOW_DOWN, null, null);
                case "authorization_declined" -> new DevicePoll(DevicePoll.Status.FAILED, null, "已拒绝微软账号授权");
                case "expired_token" -> new DevicePoll(DevicePoll.Status.FAILED, null, "授权码已过期，请重新发起");
                default -> {
                    log.warn("微软设备码轮询失败：status={}, error={}, description={}",
                            response.statusCode(), error, body.path("error_description").asText(""));
                    yield new DevicePoll(DevicePoll.Status.FAILED, null, "微软账号授权失败，请重试");
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DevicePoll(DevicePoll.Status.FAILED, null, "授权请求被中断，请重试");
        } catch (Exception e) {
            log.warn("微软设备码轮询异常：{}", e.getMessage());
            return new DevicePoll(DevicePoll.Status.FAILED, null, "微软授权服务连接失败，请稍后再试");
        }
    }

    /**
     * 用微软 token 换正版档案
     *
     * @param microsoftToken 微软 access_token
     * @return 正版档案或失败原因
     */
    public Result<PremiumProfile> resolveProfile(String microsoftToken) {
        if (microsoftToken == null || microsoftToken.isBlank())
            return Result.failure("微软授权缺失，请重新发起");
        try {
            XblSession xbl = xboxLiveAuthenticate(microsoftToken);
            String xstsToken = xstsAuthorize(xbl.token());
            String mcToken = minecraftLogin(xbl.uhs(), xstsToken);
            PremiumProfile profile = fetchProfile(mcToken);
            log.info("正版验证成功：正版 UUID={}, 名称={}", profile.uuid(), profile.name());
            return Result.succeed(profile);
        } catch (AuthException e) {
            log.warn("正版验证失败：{}", e.getMessage());
            return Result.failure(e.userMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("正版验证被中断");
            return Result.failure("正版验证被中断，请重试");
        } catch (Exception e) {
            log.error("正版验证异常", e);
            return Result.failure("正版验证服务异常，请稍后再试");
        }
    }

    /**
     * Microsoft token 换 Xbox Live 用户令牌（title client 用 t= 前缀）
     */
    private static XblSession xboxLiveAuthenticate(String msToken) throws IOException, InterruptedException {
        ObjectNode properties = MAPPER.createObjectNode()
                .put("SiteName", "user.auth.xboxlive.com")
                .put("AuthMethod", "RPS")
                .put("RpsTicket", "t=" + msToken);
        ObjectNode payload = MAPPER.createObjectNode()
                .put("RelyingParty", "http://auth.xboxlive.com")
                .put("TokenType", "JWT");
        payload.set("Properties", properties);
        HttpResponse<String> response = send(jsonPost(XBL_AUTH_URL, payload, true));
        JsonNode body = parseBody(response);
        if (response.statusCode() != 200) {
            log.warn("Xbox Live 认证失败：status={}, XErr={}", response.statusCode(), body.path("XErr").asLong(0));
            throw new AuthException("Xbox Live 认证失败，请稍后重试");
        }
        String token = body.path("Token").asText("");
        String uhs = body.path("DisplayClaims").path("xui").path(0).path("uhs").asText("");
        if (token.isEmpty() || uhs.isEmpty()) throw new AuthException("Xbox Live 认证响应异常");
        return new XblSession(token, uhs);
    }

    /**
     * XBL 令牌换 XSTS 授权令牌
     */
    private static String xstsAuthorize(String xblToken) throws IOException, InterruptedException {
        ObjectNode properties = MAPPER.createObjectNode().put("SandboxId", "RETAIL");
        ArrayNode userTokens = properties.putArray("UserTokens");
        userTokens.add(xblToken);
        ObjectNode payload = MAPPER.createObjectNode()
                .put("RelyingParty", "rp://api.minecraftservices.com/")
                .put("TokenType", "JWT");
        payload.set("Properties", properties);
        HttpResponse<String> response = send(jsonPost(XSTS_AUTH_URL, payload, true));
        JsonNode body = parseBody(response);
        if (response.statusCode() != 200) {
            long xerr = body.path("XErr").asLong(0);
            log.warn("XSTS 授权失败：status={}, XErr={}, message={}",
                    response.statusCode(), xerr, body.path("Message").asText(""));
            throw new AuthException(xstsErrorMessage(xerr));
        }
        String token = body.path("Token").asText("");
        if (token.isEmpty()) throw new AuthException("XSTS 授权响应异常");
        return token;
    }

    /**
     * XSTS 令牌换 Minecraft access_token（经典端点失败时改试启动器端点）
     */
    private static String minecraftLogin(String uhs, String xstsToken) throws IOException, InterruptedException {
        String xblToken = "XBL3.0 x=" + uhs + ";" + xstsToken;
        int firstStatus;
        try {
            return requestMinecraftToken(MC_LOGIN_URL,
                    MAPPER.createObjectNode().put("identityToken", xblToken));
        } catch (AuthException e) {
            firstStatus = e.status;
            log.warn("Minecraft 经典登录端点被拒（status={}），改试官方启动器端点", firstStatus);
        }
        try {
            return requestMinecraftToken(MC_LAUNCHER_LOGIN_URL, MAPPER.createObjectNode()
                    .put("platform", "PC_LAUNCHER")
                    .put("xtoken", xblToken));
        } catch (AuthException e) {
            throw new AuthException("Minecraft 登录失败（HTTP " + firstStatus + "/" + e.status + "），请稍后重试或联系管理员");
        }
    }

    private static String requestMinecraftToken(String url, ObjectNode payload) throws IOException, InterruptedException {
        HttpResponse<String> response = send(jsonPost(url, payload, false));
        JsonNode body = parseBody(response);
        if (response.statusCode() != 200) {
            log.warn("Minecraft 登录失败：url={}, status={}, body={}", url, response.statusCode(), truncate(response.body()));
            throw new AuthException("Minecraft 登录失败", response.statusCode());
        }
        String token = body.path("access_token").asText("");
        if (token.isEmpty()) {
            log.warn("Minecraft 登录响应缺少 access_token：url={}, status={}, body={}",
                    url, response.statusCode(), truncate(response.body()));
            throw new AuthException("Minecraft 登录响应异常", response.statusCode());
        }
        return token;
    }

    /**
     * 拉取正版档案（UUID 与名称）
     */
    private static PremiumProfile fetchProfile(String mcToken) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + mcToken)
                .header("Accept", "application/json")
                .GET()
                .build());
        if (response.statusCode() == 404) {
            log.warn("获取 Minecraft 档案失败（无 Java 版档案）：body={}", truncate(response.body()));
            throw new AuthException("该微软账号没有 Minecraft Java 版档案（未购买或未创建档案）");
        }
        if (response.statusCode() != 200) {
            log.warn("获取 Minecraft 档案失败：status={}, body={}", response.statusCode(), truncate(response.body()));
            throw new AuthException("获取 Minecraft 档案失败，请稍后重试");
        }
        JsonNode body = parseBody(response);
        String id = body.path("id").asText("");
        if (id.isEmpty()) throw new AuthException("Minecraft 档案响应异常");
        return new PremiumProfile(parseProfileId(id), body.path("name").asText(""));
    }

    private static HttpRequest jsonPost(String url, ObjectNode payload, boolean xblContract) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (xblContract) builder.header("x-xbl-contract-version", XBL_CONTRACT_VERSION);
        return builder.POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode parseBody(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.warn("正版验证收到非 JSON 响应：status={}, body={}", response.statusCode(), truncate(body));
            return MAPPER.createObjectNode();
        }
    }

    private static String xstsErrorMessage(long xerr) {
        if (xerr == 2148916233L) return "该微软账号还没有 Xbox 档案，请先登录 xbox.com 创建";
        if (xerr == 2148916235L) return "该账号所在地区不支持 Xbox Live";
        if (xerr == 2148916236L || xerr == 2148916237L) return "该账号需要进行年龄验证（仅韩国地区）";
        if (xerr == 2148916238L) return "未成年账号需先加入 Microsoft 家庭组";
        return "该微软账号无法登录 Xbox Live（错误码 " + xerr + "）";
    }

    private static UUID parseProfileId(String profileId) {
        String id = profileId.trim();
        if (!id.contains("-") && id.length() == 32)
            id = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return UUID.fromString(id);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private record XblSession(String token, String uhs) {
    }

    private static class AuthException extends RuntimeException {
        final String userMessage;
        final int status;

        AuthException(String userMessage) {
            this(userMessage, -1);
        }

        AuthException(String userMessage, int status) {
            super(userMessage);
            this.userMessage = userMessage;
            this.status = status;
        }
    }
}
