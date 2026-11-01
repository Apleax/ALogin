package xyz.apleax.ALogin.viaproxy;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.netty.channel.ChannelFutureListener;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.netty.crypto.AESEncryption;
import net.raphimc.netminecraft.netty.crypto.CryptUtil;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.impl.login.*;
import net.raphimc.vialegacy.api.util.GameProfileUtil;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.events.ClientLoggedInEvent;
import net.raphimc.viaproxy.proxy.external_interface.AuthLibServices;
import net.raphimc.viaproxy.proxy.external_interface.ExternalInterface;
import net.raphimc.viaproxy.proxy.packethandler.PacketHandler;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.CloseAndReturn;
import net.raphimc.viaproxy.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ViaProxy 数据包处理器：对命中名单的连接做会话校验并注入 HMAC 断言
 *
 * @author Apleax
 */
public class PremiumAssertionHandler extends PacketHandler {
    private static final String ASSERTION_VERSION = "ALOGIN1";
    private static final Random RANDOM = new Random();
    /**
     * 已提示过未命中名单的玩家名（每次启动只提示一次）
     */
    private static final Set<String> NOTIFIED_NAMES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final PluginConfig config;
    private final BoundPremiumList boundPremiumList;
    private final ExecutorService authExecutor;
    /**
     * 每会话独立密钥对：私钥仅解密本次会话的 AES 密钥，限制单次内存转储的爆炸半径
     */
    private final KeyPair keyPair = CryptUtil.generateKeyPair();
    private final byte[] verifyToken = new byte[4];
    private final AtomicBoolean helloForwarded = new AtomicBoolean();
    private volatile Stage stage = Stage.IDLE;
    private volatile GameProfile premiumProfile;
    private volatile String challenge;
    private volatile int challengeQueryId = -1;
    private ScheduledFuture<?> timeoutTask;

    private enum Stage {
        IDLE, HELLO_SENT, FORWARDED
    }

    public PremiumAssertionHandler(final ProxyConnection proxyConnection, final PluginConfig config,
                                   final BoundPremiumList boundPremiumList, final ExecutorService authExecutor) {
        super(proxyConnection);
        this.config = config;
        this.boundPremiumList = boundPremiumList;
        this.authExecutor = authExecutor;
        RANDOM.nextBytes(this.verifyToken);
    }

    /**
     * 处理客户端上行包；挑战应答仅在正版校验已出结果时改写（校验在途时保留挑战，避免烧掉挑战导致正版玩家拿不到断言）
     */
    @Override
    public boolean handleC2P(final Packet packet, final List<ChannelFutureListener> listeners) throws GeneralSecurityException {
        if (packet instanceof C2SLoginHelloPacket helloPacket) {
            if (stage != Stage.IDLE) throw CloseAndReturn.INSTANCE;

            if (!isFakePremium() && !boundPremiumList.contains(helloPacket.uuid)) {
                Logger.LOGGER.debug("[ALoginPremium] {} 未命中已绑定名单（claimedUuid={}，名单 {} 条），按普通连接透传",
                        helloPacket.name, helloPacket.uuid == null ? "无" : helloPacket.uuid, boundPremiumList.size());
                if (NOTIFIED_NAMES.size() < 5000 && NOTIFIED_NAMES.add(helloPacket.name))
                    Logger.LOGGER.info("[ALoginPremium] {}(claimedUuid={}) 未命中已绑定名单（当前 {} 条），按普通连接透传",
                            helloPacket.name, helloPacket.uuid == null ? "无" : helloPacket.uuid, boundPremiumList.size());
                return true;
            }
            Logger.LOGGER.info("[ALoginPremium] {} 命中已绑定正版（uuid={}），发起会话校验",
                    helloPacket.name, helloPacket.uuid);
            stage = Stage.HELLO_SENT;

            proxyConnection.setLoginHelloPacket(helloPacket);
            proxyConnection.setGameProfile(helloPacket.uuid != null
                    ? new GameProfile(helloPacket.uuid, helloPacket.name)
                    : new GameProfile(GameProfileUtil.getOfflinePlayerUuid(helloPacket.name), helloPacket.name));

            if (isFakePremium()) {
                premiumProfile = new GameProfile(PluginConfig.parseUuid(config.fakePremiumUuid()), config.fakePremiumName());
                Logger.LOGGER.info("[ALoginPremium] 测试模式：跳过校验，将按 {} 生成断言", config.fakePremiumName());
                forwardLoginHello();
            } else {
                proxyConnection.getC2P().writeAndFlush(
                                new S2CLoginHelloPacket("", keyPair.getPublic().getEncoded(), verifyToken, true))
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                timeoutTask = proxyConnection.getC2P().eventLoop()
                        .schedule(this::onVerifyTimeout, config.verifyTimeoutMs(), TimeUnit.MILLISECONDS);
            }
            return false;
        }

        if (packet instanceof C2SLoginKeyPacket keyPacket) {
            if (stage != Stage.HELLO_SENT) return true;

            final boolean nonceValid;
            if (keyPacket.encryptedNonce != null)
                nonceValid = Arrays.equals(verifyToken, CryptUtil.decryptData(keyPair.getPrivate(), keyPacket.encryptedNonce));
            else {
                final C2SLoginHelloPacket helloPacket = proxyConnection.getLoginHelloPacket();
                nonceValid = helloPacket != null && helloPacket.key != null
                        && CryptUtil.verifySignedNonce(helloPacket.key, verifyToken, keyPacket.salt, keyPacket.signature);
            }
            if (!nonceValid) {
                Logger.u_err("auth", proxyConnection, "Invalid verify token");
                proxyConnection.kickClient("§cInvalid verify token!");
                return true;
            }

            final SecretKey secretKey = CryptUtil.decryptSecretKey(keyPair.getPrivate(), keyPacket.encryptedSecretKey);
            proxyConnection.getC2P().attr(MCPipeline.ENCRYPTION_ATTRIBUTE_KEY).set(new AESEncryption(secretKey));
            authExecutor.submit(() -> verifySession(secretKey));
            return false;
        }

        if (packet instanceof C2SLoginCustomQueryAnswerPacket answerPacket
                && challenge != null && answerPacket.queryId == challengeQueryId) {
            if (premiumProfile != null) {
                final String usedChallenge = challenge;
                challenge = null;
                answerPacket.response = buildAssertion(usedChallenge);
                Logger.LOGGER.info("[ALoginPremium] 已下发正版断言：uuid={}, name={}",
                        premiumProfile.getId(), premiumProfile.getName());
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean handleP2S(final Packet packet, final List<ChannelFutureListener> listeners) {
        if (packet instanceof S2CLoginCustomQueryPacket queryPacket && config.channel().equals(queryPacket.channel)) {
            this.challengeQueryId = queryPacket.queryId;
            this.challenge = queryPacket.payload == null ? null : new String(queryPacket.payload, StandardCharsets.UTF_8);
        }
        return true;
    }

    private void verifySession(final SecretKey secretKey) {
        final String userName = proxyConnection.getGameProfile().getName();
        try {
            final String serverHash = new BigInteger(
                    CryptUtil.computeServerIdHash("", keyPair.getPublic(), secretKey)).toString(16);
            final ProfileResult profileResult = AuthLibServices.SESSION_SERVICE.hasJoinedServer(userName, serverHash, null);
            if (profileResult != null) {
                premiumProfile = profileResult.profile();
                Logger.LOGGER.info("[ALoginPremium] 正版校验通过：{} -> {}", userName, premiumProfile.getId());
            } else Logger.LOGGER.info("[ALoginPremium] {} 会话校验未通过（离线/盗版），按离线继续", userName);
        } catch (Throwable e) {
            Logger.LOGGER.warn("[ALoginPremium] 会话校验请求异常（按离线继续）：{}", e.getMessage());
        }
        proxyConnection.getC2P().eventLoop().execute(this::forwardLoginHello);
    }

    private void onVerifyTimeout() {
        if (helloForwarded.get()) return;
        Logger.LOGGER.warn("[ALoginPremium] 客户端未在 {}ms 内应答加密请求，按离线继续", config.verifyTimeoutMs());
        forwardLoginHello();
    }

    private void forwardLoginHello() {
        if (!helloForwarded.compareAndSet(false, true)) return;
        if (timeoutTask != null) timeoutTask.cancel(false);
        stage = Stage.FORWARDED;
        ViaProxy.EVENT_MANAGER.call(new ClientLoggedInEvent(proxyConnection));
        ExternalInterface.fillPlayerData(proxyConnection);
        proxyConnection.getChannel().writeAndFlush(proxyConnection.getLoginHelloPacket())
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    private byte[] buildAssertion(final String challengeValue) {
        final String uuid32 = premiumProfile.getId().toString().replace("-", "");
        final String name = premiumProfile.getName();
        final String ip = resolveClientIp();
        final String timestamp = Long.toString(System.currentTimeMillis() / 1000);
        final String canonical = challengeValue + "|" + ASSERTION_VERSION + "|" + uuid32 + "|" + name + "|" + ip + "|" + timestamp;
        final String hmac = hmacHex(canonical);
        return (ASSERTION_VERSION + "|" + uuid32 + "|" + name + "|" + ip + "|" + timestamp + "|" + hmac)
                .getBytes(StandardCharsets.UTF_8);
    }

    private String hmacHex(final String canonical) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private String resolveClientIp() {
        try {
            final SocketAddress address = proxyConnection.getC2P().remoteAddress();
            if (address instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null)
                return inetSocketAddress.getAddress().getHostAddress();
        } catch (Throwable ignored) {
        }
        return "";
    }

    private boolean isFakePremium() {
        return !config.fakePremiumUuid().isBlank();
    }
}
