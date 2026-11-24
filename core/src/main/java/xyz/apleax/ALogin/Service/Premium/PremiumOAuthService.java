package xyz.apleax.ALogin.Service.Premium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;
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
     * @param errorCode      失败原因（等待时为 null）
     */
    public record DevicePoll(Status status, String microsoftToken, ErrorCode errorCode) {
        public enum Status {PENDING, SLOW_DOWN, SUCCESS, FAILED}
    }

    /**
     * 申请设备码
     */
    public DeviceCode startDeviceCode() {
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
                throw ApiException.of(ErrorCode.PREMIUM_SERVICE_UNAVAILABLE);
            }
            String deviceCode = body.path("device_code").asText("");
            String userCode = body.path("user_code").asText("");
            if (deviceCode.isEmpty() || userCode.isEmpty()) {
                log.warn("申请微软设备码响应异常：{}", truncate(response.body()));
                throw ApiException.of(ErrorCode.PREMIUM_RESPONSE_INVALID);
            }
            return new DeviceCode(deviceCode, userCode,
                    body.path("verification_uri").asText("https://www.microsoft.com/link"),
                    Math.max(1, body.path("interval").asLong(5)),
                    Math.max(60, body.path("expires_in").asLong(900)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.of(ErrorCode.REQUEST_INTERRUPTED);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("申请微软设备码异常：{}", e.getMessage());
            throw ApiException.of(ErrorCode.PREMIUM_CONNECT_FAILED);
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
                return new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.PREMIUM_POLL_INVALID);
            }
            String error = body.path("error").asText("");
            return switch (error) {
                case "authorization_pending" -> new DevicePoll(DevicePoll.Status.PENDING, null, null);
                case "slow_down" -> new DevicePoll(DevicePoll.Status.SLOW_DOWN, null, null);
                case "authorization_declined" ->
                        new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.PREMIUM_AUTH_DECLINED);
                case "expired_token" ->
                        new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.PREMIUM_CODE_EXPIRED);
                default -> {
                    log.warn("微软设备码轮询失败：status={}, error={}, description={}",
                            response.statusCode(), error, body.path("error_description").asText(""));
                    yield new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.PREMIUM_AUTH_FAILED);
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.REQUEST_INTERRUPTED);
        } catch (Exception e) {
            log.warn("微软设备码轮询异常：{}", e.getMessage());
            return new DevicePoll(DevicePoll.Status.FAILED, null, ErrorCode.PREMIUM_CONNECT_FAILED);
        }
    }

    /**
     * 用微软 token 换正版档案
     *
     * @param microsoftToken 微软 access_token
     * @return 正版档案
     */
    public PremiumProfile resolveProfile(String microsoftToken) {
        if (microsoftToken == null || microsoftToken.isBlank())
            throw ApiException.of(ErrorCode.MICROSOFT_TOKEN_MISSING);
        try {
            XblSession xbl = xboxLiveAuthenticate(microsoftToken);
            String xstsToken = xstsAuthorize(xbl.token());
            String mcToken = minecraftLogin(xbl.uhs(), xstsToken);
            PremiumProfile profile = fetchProfile(mcToken);
            log.info("正版验证成功：正版 UUID={}, 名称={}", profile.uuid(), profile.name());
            return profile;
        } catch (AuthException e) {
            log.warn("正版验证失败：{}", e.getMessage());
            throw e.args == null || e.args.length == 0
                    ? ApiException.causedBy(e.code, e)
                    : ApiException.causedBy(e.code, e, e.args);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("正版验证被中断");
            throw ApiException.of(ErrorCode.PREMIUM_VERIFY_INTERRUPTED);
        } catch (Exception e) {
            log.error("正版验证异常", e);
            throw ApiException.of(ErrorCode.PREMIUM_VERIFY_FAILED);
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
            throw new AuthException(ErrorCode.XBOX_AUTH_FAILED);
        }
        String token = body.path("Token").asText("");
        String uhs = body.path("DisplayClaims").path("xui").path(0).path("uhs").asText("");
        if (token.isEmpty() || uhs.isEmpty()) throw new AuthException(ErrorCode.XBOX_RESPONSE_INVALID);
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
            ErrorCode code = xstsErrorCode(xerr);
            throw code == ErrorCode.XSTS_AUTH_FAILED ? new AuthException(code, xerr) : new AuthException(code);
        }
        String token = body.path("Token").asText("");
        if (token.isEmpty()) throw new AuthException(ErrorCode.XSTS_RESPONSE_INVALID);
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
            throw new AuthException(ErrorCode.MINECRAFT_LOGIN_FAILED, firstStatus, e.status);
        }
    }

    private static String requestMinecraftToken(String url, ObjectNode payload) throws IOException, InterruptedException {
        HttpResponse<String> response = send(jsonPost(url, payload, false));
        JsonNode body = parseBody(response);
        if (response.statusCode() != 200) {
            log.warn("Minecraft 登录失败：url={}, status={}, body={}", url, response.statusCode(), truncate(response.body()));
            throw new AuthException(ErrorCode.MINECRAFT_LOGIN_FAILED_SIMPLE, response.statusCode());
        }
        String token = body.path("access_token").asText("");
        if (token.isEmpty()) {
            log.warn("Minecraft 登录响应缺少 access_token：url={}, status={}, body={}",
                    url, response.statusCode(), truncate(response.body()));
            throw new AuthException(ErrorCode.MINECRAFT_RESPONSE_INVALID, response.statusCode());
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
            throw new AuthException(ErrorCode.MINECRAFT_PROFILE_ABSENT);
        }
        if (response.statusCode() != 200) {
            log.warn("获取 Minecraft 档案失败：status={}, body={}", response.statusCode(), truncate(response.body()));
            throw new AuthException(ErrorCode.MINECRAFT_PROFILE_FAILED);
        }
        JsonNode body = parseBody(response);
        String id = body.path("id").asText("");
        if (id.isEmpty()) throw new AuthException(ErrorCode.PREMIUM_VERIFY_INVALID);
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

    private static ErrorCode xstsErrorCode(long xerr) {
        if (xerr == 2148916233L) return ErrorCode.XSTS_NO_XBOX_PROFILE;
        if (xerr == 2148916235L) return ErrorCode.XSTS_REGION_UNSUPPORTED;
        if (xerr == 2148916236L || xerr == 2148916237L) return ErrorCode.XSTS_AGE_VERIFICATION;
        if (xerr == 2148916238L) return ErrorCode.XSTS_FAMILY_REQUIRED;
        return ErrorCode.XSTS_AUTH_FAILED;
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
        final ErrorCode code;
        final int status;
        final Object[] args;

        AuthException(ErrorCode code) {
            this(code, -1, null);
        }

        AuthException(ErrorCode code, int status) {
            this(code, status, null);
        }

        AuthException(ErrorCode code, Object... args) {
            this(code, -1, args);
        }

        private AuthException(ErrorCode code, int status, Object[] args) {
            super(code.message());
            this.code = code;
            this.status = status;
            this.args = args;
        }
    }
}
