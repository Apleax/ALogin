package xyz.apleax.ALogin.Service.User;

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.util.ResourceUtil;
import xyz.apleax.ALogin.Config.AvatarConfig;
import xyz.apleax.ALogin.Util.AvatarRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认头像（兜底层）：按 {@code Avatar.default-source} 取图并缩放到请求边长；
 * 未配置或取图失败时返回内置占位头像——接口因此**永远**能返回一张 PNG。
 * <p>
 * 取图结果走 {@code AvatarDefaultCache}（在 {@code CaffeineConfig} 里声明）：加载失败返回 {@code null}，
 * Caffeine 不缓存 null，所以配置修好后下一次请求立即生效，且不会把失败结果缓存住。
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class AvatarDefaultProvider {
    /**
     * 默认头像文件/响应的字节上限，防止误配置一个大文件把内存吃掉
     */
    private static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;
    /**
     * 失败日志节流间隔：加载失败不缓存，若配置一直错，避免每次请求都刷日志
     */
    private static final long WARN_INTERVAL_MS = 60_000L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final AtomicLong LAST_WARN_AT = new AtomicLong(0L);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final LoadingCache<@NotNull String, BufferedImage> sourceCache;

    public AvatarDefaultProvider(@Inject("AvatarDefaultCache") LoadingCache<@NotNull String, BufferedImage> sourceCache) {
        this.sourceCache = sourceCache;
    }

    /**
     * 兜底头像 PNG；永不返回 {@code null}
     */
    public byte[] load(int size) {
        int side = AvatarRenderer.clampSize(size);
        String source = AvatarConfig.defaultSource();
        if (source.isEmpty()) return AvatarRenderer.placeholder(side);
        BufferedImage image = sourceCache.get(source);
        byte[] png = AvatarRenderer.resizeSmooth(image, side);
        return png != null ? png : AvatarRenderer.placeholder(side);
    }

    /**
     * 缓存加载器：把配置的来源（{@code http(s)} URL 或相对 {@code ./ALogin/} 的文件）解析成图片；失败返回 {@code null}
     */
    public static BufferedImage resolve(String source) {
        try {
            String trimmed = source == null ? "" : source.trim();
            if (trimmed.isEmpty()) return null;
            byte[] bytes = trimmed.startsWith("http://") || trimmed.startsWith("https://")
                    ? fetchRemote(trimmed)
                    : readLocalFile(trimmed);
            BufferedImage image = AvatarRenderer.decode(bytes);
            if (image == null) warnThrottled("默认头像解析失败（按占位头像处理）：source={}", trimmed);
            return image;
        } catch (Exception e) {
            warnThrottled("默认头像加载异常（按占位头像处理）：source={}，原因={}", source, e.getMessage());
            return null;
        }
    }

    /**
     * 远程默认头像：要求 200，读满上限即放弃
     */
    private static byte[] fetchRemote(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(AvatarConfig.fetchTimeoutMs()))
                .header("User-Agent", "ALogin")
                .header("Accept", "image/*")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                log.debug("默认头像下载失败：status={}, url={}", response.statusCode(), url);
                return null;
            }
            return readLimited(in, "默认头像下载");
        }
    }

    /**
     * 本地默认头像：优先 {@code ./ALogin/} 下的外部文件，回退 classpath（与 {@code MailUtil.loadTemplate} 同一约定）
     */
    private static byte[] readLocalFile(String relativePath) throws IOException {
        String fullPath = Solon.cfg().appName() + "/" + relativePath;
        URL external = ResourceUtil.getResourceByFile("./" + fullPath);
        if (external != null) try (InputStream in = external.openStream()) {
            return readLimited(in, "默认头像文件");
        }
        try (InputStream in = ResourceUtil.getResourceAsStream(fullPath)) {
            return in == null ? null : readLimited(in, "默认头像文件");
        }
    }

    /**
     * 读取上限 +1 字节以判断是否超限
     */
    private static byte[] readLimited(InputStream in, String what) throws IOException {
        byte[] data = in.readNBytes(MAX_SOURCE_BYTES + 1);
        if (data.length > MAX_SOURCE_BYTES) {
            warnThrottled("{}超过大小上限 {} 字节（按占位头像处理）", what, MAX_SOURCE_BYTES);
            return null;
        }
        return data;
    }

    /**
     * 失败日志节流（每分钟最多一条）
     */
    private static void warnThrottled(String format, Object... args) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_AT.get();
        if (now - last < WARN_INTERVAL_MS) return;
        if (!LAST_WARN_AT.compareAndSet(last, now)) return;
        log.warn(format, args);
    }
}
