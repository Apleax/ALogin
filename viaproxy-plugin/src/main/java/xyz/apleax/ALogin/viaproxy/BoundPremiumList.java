package xyz.apleax.ALogin.viaproxy;

import net.raphimc.viaproxy.util.logging.Logger;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ALogin 已绑定正版 UUID 名单缓存
 *
 * @author Apleax
 */
public final class BoundPremiumList {
    /**
     * 从响应体中提取 UUID（JSON 包装或纯文本均适用）
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String KEY_HEADER = "X-ALogin-Key";
    private static final long MIN_REFRESH_SECONDS = 5L;
    /**
     * 响应体大小上限：1 MB，防止异常端点打爆内存
     */
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    /**
     * 名单连续拉取失败超过此时长后，周期性报 ERROR（仍沿用旧名单，fail-open）
     */
    private static final long STALE_ERROR_MILLIS = 5 * 60 * 1000L;
    private static final long STALE_ERROR_INTERVAL_MILLIS = 10 * 60 * 1000L;

    private final xyz.apleax.ALogin.viaproxy.PluginConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "ALoginPremium-BoundList");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Set<UUID> uuids = Set.of();
    private volatile boolean loaded;
    private volatile String lastError;
    private volatile long lastSuccessMillis;
    private volatile long lastStaleErrorMillis;

    public BoundPremiumList(final xyz.apleax.ALogin.viaproxy.PluginConfig config) {
        this.config = config;
    }

    public void start() {
        final long seconds = Math.max(MIN_REFRESH_SECONDS, config.boundListRefreshSeconds());
        warnIfPlaintextRemote();
        Logger.LOGGER.info("[ALoginPremium] 已启动已绑定正版名单刷新：每 {} 秒拉取一次 {}", seconds, listUrl());
        scheduler.scheduleWithFixedDelay(this::refresh, 0, seconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * 客户端声称的 UUID 是否属于已绑定正版
     */
    public boolean contains(final UUID uuid) {
        return uuid != null && uuids.contains(uuid);
    }

    /**
     * 当前名单条目数（诊断日志用）
     */
    public int size() {
        return uuids.size();
    }

    /**
     * 远端地址用明文 http 传输共享密钥时告警（密钥可伪造任意正版断言）
     */
    private void warnIfPlaintextRemote() {
        String url = listUrl();
        if (!url.startsWith("http://")) return;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && !isLoopback(host))
                Logger.LOGGER.warn("[ALoginPremium] alogin-url 为远端明文 http，共享密钥将明文传输，建议改用 https 或本机/内网地址");
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static boolean isLoopback(String host) {
        if ("localhost".equalsIgnoreCase(host)) return true;
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private void refresh() {
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(listUrl()))
                    .header(KEY_HEADER, config.secret())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                fail("HTTP " + response.statusCode() + (response.statusCode() == 403
                        ? "（secret 与登录服 Premium.assertion.secret 不一致？）" : ""));
                return;
            }
            final byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (body.length > MAX_BODY_BYTES) {
                fail("响应体超过 " + MAX_BODY_BYTES + " 字节，已丢弃");
                return;
            }
            final String text = new String(body, StandardCharsets.UTF_8);
            final Set<UUID> parsed = new HashSet<>();
            final Matcher matcher = UUID_PATTERN.matcher(text);
            while (matcher.find()) parsed.add(UUID.fromString(matcher.group()));
            final boolean changed = !loaded || !parsed.equals(uuids);
            uuids = Set.copyOf(parsed);
            loaded = true;
            lastSuccessMillis = System.currentTimeMillis();
            if (changed || lastError != null)
                Logger.LOGGER.info("[ALoginPremium] 已加载已绑定正版名单：{} 个{}", uuids.size(),
                        lastError == null ? "" : "（此前拉取失败：" + lastError + "，已恢复）");
            if (changed) Logger.LOGGER.debug("[ALoginPremium] 名单内容：{}", uuids);
            lastError = null;
        } catch (Exception e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 拉取失败：保留旧名单（fail-open），失败原因变化时记日志；长期失败周期性报 ERROR
     */
    private void fail(final String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            Logger.LOGGER.warn("[ALoginPremium] 拉取已绑定正版名单失败（继续沿用 {} 个旧记录）：{}", uuids.size(), message);
        }
        long now = System.currentTimeMillis();
        if (loaded && lastSuccessMillis > 0 && now - lastSuccessMillis > STALE_ERROR_MILLIS
                && now - lastStaleErrorMillis > STALE_ERROR_INTERVAL_MILLIS) {
            lastStaleErrorMillis = now;
            Logger.LOGGER.error("[ALoginPremium] 已绑定正版名单已陈旧 {} 分钟仍在沿用：解绑/新绑定的玩家无法及时生效",
                    (now - lastSuccessMillis) / 60000L);
        }
    }

    private String listUrl() {
        return config.aloginUrl().replaceAll("/+$", "") + "/api/internal/premium/BoundUuids";
    }
}
