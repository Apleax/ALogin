package xyz.apleax.ALogin.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * IP 归属地查询工具：调用 ipwho.is 免费接口获取国家/城市，用于异地登录提醒邮件展示地区。
 *
 * @author Apleax
 * @see <a href="https://ipwho.is/">ipwho.is 文档</a>
 */
@Slf4j
public final class IpLocationUtil {
    private static final String API_TEMPLATE = "https://ipwho.is/%s?lang=zh-CN&fields=success,country,city";
    private static final String UNKNOWN = "未知";
    private static final Location UNKNOWN_LOCATION = new Location("", "");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Cache<String, Location> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public record Location(String country, String city) {
        public Location {
            country = country == null ? "" : country.trim();
            city = city == null ? "" : city.trim();
        }

        public boolean isComplete() {
            return !country.isEmpty() && !city.isEmpty();
        }

        public String displayName() {
            if (country.isEmpty() && city.isEmpty()) return UNKNOWN;
            if (city.isEmpty() || city.equals(country)) return country;
            if (country.isEmpty()) return city;
            return country + " " + city;
        }
    }

    private IpLocationUtil() {
    }

    public static void clearCache() {
        CACHE.invalidateAll();
    }

    public static CompletableFuture<String> queryAsync(String ip) {
        return queryLocationAsync(ip).thenApply(Location::displayName);
    }

    public static CompletableFuture<Boolean> isSameLocationAsync(String oldIp, String newIp) {
        if (oldIp == null || oldIp.isBlank() || newIp == null || newIp.isBlank())
            return CompletableFuture.completedFuture(false);
        if (oldIp.trim().equals(newIp.trim())) return CompletableFuture.completedFuture(true);
        return queryLocationAsync(oldIp).thenCombine(queryLocationAsync(newIp),
                (oldLocation, newLocation) -> oldLocation.isComplete()
                        && newLocation.isComplete() && oldLocation.equals(newLocation));
    }

    public static CompletableFuture<Location> queryLocationAsync(String ip) {
        if (ip == null || ip.isBlank()) return CompletableFuture.completedFuture(UNKNOWN_LOCATION);
        String key = ip.trim();
        Location cached = CACHE.getIfPresent(key);
        if (cached != null) {
            log.debug("IP 归属地命中缓存，ip={}, location={}", key, cached);
            return CompletableFuture.completedFuture(cached);
        }
        log.debug("IP 归属地发起查询，ip={}", key);

        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(String.format(API_TEMPLATE, key)))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "ALogin")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("IP 格式非法，无法查询归属地，ip={}", key);
            return CompletableFuture.completedFuture(UNKNOWN_LOCATION);
        }

        try {
            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        Location location = parse(key, resp);
                        if (location.isComplete()) CACHE.put(key, location);
                        return location;
                    })
                    .completeOnTimeout(UNKNOWN_LOCATION, TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(e -> {
                        log.warn("IP 归属地查询异常，ip={}, err={}", key, e.getMessage());
                        return UNKNOWN_LOCATION;
                    });
        } catch (RuntimeException e) {
            log.warn("IP 归属地请求提交失败，ip={}, err={}", key, e.getMessage());
            return CompletableFuture.completedFuture(UNKNOWN_LOCATION);
        }
    }

    private static Location parse(String ip, HttpResponse<String> resp) {
        if (resp.statusCode() != 200) {
            log.debug("IP 归属地查询失败，ip={}, status={}", ip, resp.statusCode());
            return UNKNOWN_LOCATION;
        }
        try {
            JsonNode node = MAPPER.readTree(resp.body());
            if (node.has("success") && !node.path("success").asBoolean(true)) {
                log.debug("IP 归属地查询返回失败，ip={}, msg={}", ip, node.path("message").asText(""));
                return UNKNOWN_LOCATION;
            }
            String country = node.path("country").asText("").trim();
            String city = node.path("city").asText("").trim();
            log.debug("IP 归属地响应解析，ip={}, country='{}', city='{}', body={}", ip, country, city, resp.body());
            return new Location(country, city);
        } catch (Exception e) {
            log.warn("IP 归属地响应解析失败，ip={}, err={}", ip, e.getMessage());
            return UNKNOWN_LOCATION;
        }
    }
}
