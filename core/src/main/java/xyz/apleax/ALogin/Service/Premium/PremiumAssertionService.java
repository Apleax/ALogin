package xyz.apleax.ALogin.Service.Premium;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 正版断言校验：校验 ViaProxy 插件经登录插件消息回传的 HMAC 签名断言
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class PremiumAssertionService {
    private static final String VERSION_TAG = "ALOGIN1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_PAYLOAD_LENGTH = 512;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern HEX32_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    @Inject("${Premium.assertion.enabled:true}")
    private static boolean enabled;
    @Inject("${Premium.assertion.secret:}")
    private static String secret;
    @Inject("${Premium.assertion.channel:alogin:premium}")
    private static String channel;
    @Inject("${Premium.assertion.timeout-ms:3500}")
    private static long timeoutMs;
    @Inject("${Premium.assertion.clock-skew-seconds:120}")
    private static long clockSkewSeconds;

    /**
     * 会话 UUID → 正版断言（仅存校验通过的）
     */
    private static final Cache<UUID, PremiumAssertion> SESSION_ASSERTIONS = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /**
     * 断言功能是否可用
     */
    public boolean isEnabled() {
        return enabled && secret != null && !secret.isBlank();
    }

    @Init
    public void onInit() {
        if (isEnabled()) log.info("正版免密断言已启用：channel={}, timeout={}ms", channel, timeoutMs);
        else log.info("正版免密断言未启用（未配置 secret 或已关闭），所有连接按普通流程处理");
    }

    /**
     * 校验内部调用携带的共享密钥（常量时间比较）
     */
    public boolean matchesSharedKey(String key) {
        if (!isEnabled() || key == null) return false;
        return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }

    public String channel() {
        return channel;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * 生成一次性随机挑战（32 位十六进制）
     */
    public String createChallenge() {
        return RandomStringUtils.generateHex(32);
    }

    public void stash(UUID sessionUuid, PremiumAssertion assertion) {
        SESSION_ASSERTIONS.put(sessionUuid, assertion);
    }

    public PremiumAssertion get(UUID sessionUuid) {
        return SESSION_ASSERTIONS.getIfPresent(sessionUuid);
    }

    public void discard(UUID sessionUuid) {
        SESSION_ASSERTIONS.invalidate(sessionUuid);
    }

    /**
     * 校验断言载荷
     *
     * @param challenge    本次连接下发的一次性挑战
     * @param expectedName 本次连接 LoginStart 的名字（大小写不敏感）
     * @param payload      插件应答的载荷，可为 null
     * @return 校验通过返回断言，否则返回 null
     */
    public PremiumAssertion verify(String challenge, String expectedName, byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        if (payload.length > MAX_PAYLOAD_LENGTH) {
            log.warn("正版断言过长：{} 字节", payload.length);
            return null;
        }
        String[] parts = new String(payload, StandardCharsets.UTF_8).trim().split("\\|", -1);
        if (parts.length != 6 || !VERSION_TAG.equals(parts[0])) {
            log.warn("正版断言格式非法（可能是旧版本插件）");
            return null;
        }
        String uuid32 = parts[1];
        String name = parts[2];
        String ip = parts[3];
        String ts = parts[4];
        if (!HEX32_PATTERN.matcher(uuid32).matches() || !NAME_PATTERN.matcher(name).matches()) {
            log.warn("正版断言字段非法");
            return null;
        }
        if (!name.equalsIgnoreCase(expectedName)) {
            log.warn("正版断言名字不匹配：assertion={}, login={}", name, expectedName);
            return null;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            log.warn("正版断言时间戳非法：{}", ts);
            return null;
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - timestamp) > clockSkewSeconds) {
            log.warn("正版断言超出有效时间窗：ts={}", ts);
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String canonical = challenge + "|" + VERSION_TAG + "|" + uuid32 + "|" + name + "|" + ip + "|" + ts;
            byte[] signature = HexFormat.of().parseHex(parts[5]);
            if (!MessageDigest.isEqual(signature, mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)))) {
                log.warn("正版断言签名不匹配");
                return null;
            }
        } catch (Exception e) {
            log.warn("正版断言校验异常：{}", e.getMessage());
            return null;
        }
        UUID uuid = UUID.fromString(uuid32.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
        String realIp = normalizeIp(ip);
        log.info("正版断言校验通过：uuid={}, name={}, ip={}", uuid, name, realIp);
        return new PremiumAssertion(uuid, name, realIp, timestamp);
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45 || ip.indexOf('/') >= 0
                || ip.chars().anyMatch(Character::isWhitespace)) return null;
        return ip;
    }
}
