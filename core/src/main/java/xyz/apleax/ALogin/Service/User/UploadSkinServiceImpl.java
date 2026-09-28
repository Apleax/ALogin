package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mineskin.MineSkinClient;
import org.mineskin.data.CodeAndMessage;
import org.mineskin.data.SkinInfo;
import org.mineskin.data.Variant;
import org.mineskin.data.Visibility;
import org.mineskin.exception.MineSkinRequestException;
import org.mineskin.request.GenerateRequest;
import org.mineskin.response.MineSkinResponse;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.core.handle.UploadedFile;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.Util.MailUtil;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 皮肤上传
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class UploadSkinServiceImpl {
    private static final int MOJANG_BROKEN_UUID_LENGTH = 32;
    /**
     * 皮肤文件最大字节数：200 KB
     */
    private static final int MAX_SKIN_FILE_SIZE = 200 * 1024;
    /**
     * Minecraft 官方皮肤宽度
     */
    private static final int SKIN_WIDTH = 64;
    /**
     * Minecraft 官方皮肤高度
     */
    private static final int SKIN_HEIGHT_MODERN = 64;
    /**
     * 等待 MineSkin 处理完成的超时时间（秒）
     */
    private static final long MINESKIN_TIMEOUT_SECONDS = 30L;
    /**
     * MineSkin 请求失败的最大尝试次数（含首次）
     */
    private static final int MINESKIN_MAX_ATTEMPTS = 3;
    /**
     * 写库死锁的最大尝试次数（含首次）
     */
    private static final int DB_MAX_ATTEMPTS = 3;

    /**
     * 皮肤异步处理执行器：每任务分配一虚拟线程
     */
    private static final ExecutorService SKIN_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final MineSkinClient skinsClient;
    private final SkinPersistenceService skinPersistenceService;

    public UploadSkinServiceImpl(
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject MineSkinClient skinsClient,
            @Inject SkinPersistenceService skinPersistenceService) {
        this.accountCache = accountCache;
        this.skinsClient = skinsClient;
        this.skinPersistenceService = skinPersistenceService;
    }

    /**
     * 皮肤上传入口：同步只做校验，皮肤格式正确立即返回，实际处理转异步。
     */
    public Result<Boolean> uploadSkin(UploadedFile skin, String variant) throws IOException {
        String extension = skin.getExtension();
        if (extension == null || !extension.equalsIgnoreCase("png"))
            return Result.failure("仅支持 PNG 格式的皮肤文件", false);

        String account = StpUtil.getLoginIdAsString();
        if (account == null) return Result.succeed(false);
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.succeed(false);

        byte[] skinData;
        try {
            skinData = skin.getContentAsBytes();
        } finally {
            skin.delete();
        }
        if (skinData == null || skinData.length == 0) {
            log.warn("皮肤文件为空，用户: {}", account);
            return Result.failure("皮肤文件为空", false);
        }
        if (skinData.length > MAX_SKIN_FILE_SIZE) {
            log.warn("皮肤文件超过大小上限，用户: {}，size={} bytes", account, skinData.length);
            return Result.failure("皮肤文件不能超过 200KB", false);
        }
        if (!isValidSkinDimension(skinData)) {
            log.warn("皮肤尺寸不合法，用户: {}", account);
            return Result.failure("皮肤尺寸必须为 64x64 像素", false);
        }

        processSkinAsync(account, accountPO.getMcUuid(), accountPO.getNickName(), accountPO.getEmail(), variant, skinData);
        return Result.succeed(true, "皮肤上传成功，正在处理中");
    }

    /**
     * 异步处理皮肤：MineSkin 提交（网络失败重试）→ 写库（死锁重试）→ 失效缓存；
     * 任一环节最终失败则发邮件提醒用户。
     */
    private void processSkinAsync(String account, UUID mcUuid, String nickName, String email, String variant, byte[] skinData) {
        SKIN_EXECUTOR.submit(() -> {
            try {
                SkinInfo skinInfo = submitToMineSkin(account, nickName, variant, skinData);

                UUID skinUuid;
                try {
                    skinUuid = parseMojangUuid(skinInfo.uuid());
                } catch (IllegalArgumentException e) {
                    throw new SkinProcessException("皮肤数据处理失败，请重试", e);
                }

                List<GameProfile.Property> properties = Collections.singletonList(new GameProfile.Property(
                        "textures",
                        skinInfo.texture().data().value(),
                        skinInfo.texture().data().signature()));

                writeSkinWithDeadlockRetry(account, mcUuid, skinUuid, skinData, properties);

                accountCache.invalidate(account);
                log.debug("皮肤异步处理成功，用户: {}", account);
            } catch (SkinProcessException e) {
                log.warn("皮肤处理失败，用户: {}，原因: {}", account, e.getMessage());
                MailUtil.sendSkinUploadFailedAsync(email, account, e.userMessage);
            } catch (Exception e) {
                log.error("皮肤异步处理异常，用户: {}", account, e);
                MailUtil.sendSkinUploadFailedAsync(email, account, "服务器处理异常，请稍后重试或联系管理员");
            }
        });
    }

    /**
     * 提交 MineSkin 并等待完成：网络类失败（超时/IO）自动重试，业务类失败（图片被拒）不重试。
     *
     * @param account  用户账号
     * @param nickName 玩家昵称
     * @param variant  皮肤类型（宽体/纤细）
     * @param skinData 皮肤图片字节数组
     */
    private SkinInfo submitToMineSkin(String account, String nickName, String variant, byte[] skinData) {
        int attempt = 0;
        Variant playerVariant = Variant.AUTO;
        if (variant != null) if (variant.equalsIgnoreCase("SLIM")) playerVariant = Variant.SLIM;
        else if (variant.equalsIgnoreCase("CLASSIC")) playerVariant = Variant.CLASSIC;
        while (true) {
            attempt++;
            try {
                GenerateRequest request = GenerateRequest.upload(new ByteArrayInputStream(skinData))
                        .name(nickName)
                        .variant(playerVariant)
                        .visibility(Visibility.PUBLIC);
                return skinsClient.queue().submit(request)
                        .thenCompose(queueResponse -> queueResponse.getJob().waitForCompletion(skinsClient))
                        .thenCompose(jobResponse -> jobResponse.getOrLoadSkin(skinsClient))
                        .get(MINESKIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                if (attempt >= MINESKIN_MAX_ATTEMPTS) throw new SkinProcessException("皮肤处理超时，请稍后重试", e);
                log.warn("MineSkin 处理超时，重试 {}/{}，用户: {}", attempt, MINESKIN_MAX_ATTEMPTS, account);
                sleepBeforeRetry(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SkinProcessException("皮肤处理被中断", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!isNetworkError(cause) || attempt >= MINESKIN_MAX_ATTEMPTS) {
                    logMineSkinError(cause);
                    throw new SkinProcessException("皮肤处理失败，请确认图片符合要求后重试", e);
                }
                log.warn("MineSkin 网络异常，重试 {}/{}，用户: {}，原因: {}",
                        attempt, MINESKIN_MAX_ATTEMPTS, account, cause == null ? "unknown" : cause.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
    }

    private void writeSkinWithDeadlockRetry(String account, UUID mcUuid, UUID skinUuid, byte[] skinData,
                                            List<GameProfile.Property> properties) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                skinPersistenceService.writeSkinAndAccount(account, mcUuid, skinUuid, skinData, properties);
                return;
            } catch (RuntimeException e) {
                if (!isDeadlock(e) || attempt >= DB_MAX_ATTEMPTS) throw e;
                log.warn("皮肤写库死锁，重试 {}/{}，用户: {}", attempt, DB_MAX_ATTEMPTS, account);
                sleepBeforeRetry(attempt);
            }
        }
    }

    /**
     * 只读图片头判断是否为 Minecraft 官方支持的皮肤尺寸（64x64）
     */
    private static boolean isValidSkinDimension(byte[] skinData) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(skinData))) {
            if (iis == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                return w == SKIN_WIDTH && h == SKIN_HEIGHT_MODERN;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.warn("皮肤图片解析失败: {}", e.getMessage());
            return false;
        }
    }

    private static void logMineSkinError(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null)
            throwable = completionException.getCause();
        if (throwable instanceof MineSkinRequestException requestException) {
            MineSkinResponse<?> response = requestException.getResponse();
            Optional<CodeAndMessage> detailsOptional = response.getErrorOrMessage();
            detailsOptional.ifPresent(details ->
                    log.error("MineSkin 错误 {}: {}", details.code(), details.message()));
            return;
        }
        log.error("UploadSkinError", throwable);
    }

    private static boolean isNetworkError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            // MineSkin 明确返回的业务错误（如图片不合规）不可重试
            if (c instanceof MineSkinRequestException) return false;
            // 连接/读取失败等 IO 异常（含 SocketTimeout/Connect/UnknownHost）视为网络问题，可重试
            if (c instanceof IOException) return true;
        }
        return false;
    }

    private static boolean isDeadlock(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if (state != null && state.startsWith("40")) return true;
                if (sqlEx.getErrorCode() == 1213) return true;
            }
            String msg = c.getMessage();
            if (msg != null && msg.contains("Deadlock found")) return true;
        }
        return false;
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static UUID parseMojangUuid(String uuidString) {
        if (uuidString == null || uuidString.isEmpty())
            throw new IllegalArgumentException("UUID string cannot be null or empty");

        if (uuidString.contains("-")) return UUID.fromString(uuidString);

        if (uuidString.length() != MOJANG_BROKEN_UUID_LENGTH)
            throw new IllegalArgumentException("Invalid UUID string length: " + uuidString.length());

        String formattedUuid = String.format("%s-%s-%s-%s-%s",
                uuidString.substring(0, 8),
                uuidString.substring(8, 12),
                uuidString.substring(12, 16),
                uuidString.substring(16, 20),
                uuidString.substring(20, 32));

        return UUID.fromString(formattedUuid);
    }
    
    private static class SkinProcessException extends RuntimeException {
        final String userMessage;

        SkinProcessException(String userMessage, Throwable cause) {
            super(userMessage, cause);
            this.userMessage = userMessage;
        }
    }
}
