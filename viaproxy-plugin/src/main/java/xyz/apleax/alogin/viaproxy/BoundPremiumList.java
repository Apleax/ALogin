package xyz.apleax.alogin.viaproxy;

import net.raphimc.viaproxy.util.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private final PluginConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "ALoginPremium-BoundList");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Set<UUID> uuids = Set.of();
    private volatile boolean loaded;
    private volatile String lastError;

    public BoundPremiumList(final PluginConfig config) {
        this.config = config;
    }

    public void start() {
        final long seconds = Math.max(MIN_REFRESH_SECONDS, config.boundListRefreshSeconds());
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

    private void refresh() {
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(listUrl()))
                    .header(KEY_HEADER, config.secret())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                fail("HTTP " + response.statusCode() + (response.statusCode() == 403
                        ? "（secret 与登录服 Premium.assertion.secret 不一致？）" : ""));
                return;
            }
            final Set<UUID> parsed = new HashSet<>();
            final Matcher matcher = UUID_PATTERN.matcher(response.body());
            while (matcher.find()) parsed.add(UUID.fromString(matcher.group()));
            final boolean changed = !loaded || parsed.size() != uuids.size();
            uuids = Set.copyOf(parsed);
            loaded = true;
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
     * 拉取失败：保留旧名单，仅失败原因变化时记日志
     */
    private void fail(final String message) {
        if (message.equals(lastError)) return;
        lastError = message;
        Logger.LOGGER.warn("[ALoginPremium] 拉取已绑定正版名单失败（继续沿用 {} 个旧记录）：{}", uuids.size(), message);
    }

    private String listUrl() {
        return config.aloginUrl().replaceAll("/+$", "") + "/api/internal/premium/BoundUuids";
    }
}
