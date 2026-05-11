package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mineskin.MineSkinClient;
import org.mineskin.data.CodeAndMessage;
import org.mineskin.data.JobInfo;
import org.mineskin.data.Visibility;
import org.mineskin.exception.MineSkinRequestException;
import org.mineskin.request.GenerateRequest;
import org.mineskin.response.MineSkinResponse;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.handle.Result;
import org.noear.solon.core.handle.UploadedFile;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.PO.SkinPO;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.SQL.Service.ISkinService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Slf4j
@Managed
@DamiTopic("user")
public class UploadSkinServiceImpl {
    private static final int MOJANG_BROKEN_UUID_LENGTH = 32;

    private final IAccountService accountService;
    private final ISkinService skinService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final MineSkinClient skinsClient;

    public UploadSkinServiceImpl(
            @Ds("DataBase") IAccountService accountService,
            @Ds("DataBase") ISkinService skinService,
            @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
            @Inject MineSkinClient skinsClient) {
        this.accountService = accountService;
        this.skinService = skinService;
        this.accountCache = accountCache;
        this.skinsClient = skinsClient;
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


    @Transaction
    public Result<Boolean> uploadSkin(UploadedFile skin) throws IOException {
        if (!skin.getExtension().equals("png")) return Result.succeed(false);
        String account = StpUtil.getLoginIdAsString();
        if (account == null) return Result.succeed(false);
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) return Result.succeed(false);
        byte[] skinData = skin.getContentAsBytes();
        if (skinData == null || skinData.length == 0) {
            log.error("Skin file is empty");
            return Result.succeed(false);
        }
        try {
            GenerateRequest request = GenerateRequest.upload(new ByteArrayInputStream(skinData))
                    .name(accountPO.getNickName())
                    .visibility(Visibility.PUBLIC);
            skinsClient.queue().submit(request)
                    .thenCompose(queueResponse -> {
                        JobInfo job = queueResponse.getJob();
                        return job.waitForCompletion(skinsClient);
                    })
                    .thenCompose(jobResponse -> jobResponse.getOrLoadSkin(skinsClient))
                    .thenAccept(skinInfo -> {
                        List<GameProfile.Property> properties = Collections.singletonList(new GameProfile.Property(
                                "textures",
                                skinInfo.texture().data().value(),
                                skinInfo.texture().data().signature()));
                        accountPO.setProperties(properties);

                        UUID skinUuid = parseMojangUuid(skinInfo.uuid());

                        SkinPO existingSkin = skinService.getOne(new LambdaQueryWrapper<SkinPO>()
                                .eq(SkinPO::getMcUuid, "\"" + accountPO.getMcUuid() + "\""));
                        boolean skinUpdated;
                        if (existingSkin != null) {
                            existingSkin.setSkinUuid(skinUuid);
                            existingSkin.setSkinImg(skinData);
                            skinUpdated = skinService.update(existingSkin, new LambdaUpdateWrapper<SkinPO>()
                                    .eq(SkinPO::getMcUuid, "\"" + accountPO.getMcUuid() + "\"")
                                    .set(SkinPO::getSkinImg, skinData));
                        } else {
                            SkinPO newSkin = new SkinPO();
                            newSkin.setMcUuid(accountPO.getMcUuid());
                            newSkin.setSkinUuid(skinUuid);
                            newSkin.setSkinImg(skinData);
                            skinUpdated = skinService.save(newSkin);
                        }
                        boolean accountUpdated = accountService.update(accountPO, new LambdaUpdateWrapper<AccountPO>()
                                .eq(AccountPO::getAccount, account));
                        if (!accountUpdated || !skinUpdated) return;
                        accountCache.put(account, accountPO);
                    })
                    .exceptionally(throwable -> {
                        log.error("UploadSkinError", throwable);
                        if (throwable instanceof CompletionException completionException)
                            throwable = completionException.getCause();
                        if (throwable instanceof MineSkinRequestException requestException) {
                            MineSkinResponse<?> response = requestException.getResponse();
                            Optional<CodeAndMessage> detailsOptional = response.getErrorOrMessage();
                            detailsOptional.ifPresent(details -> log.error("{}: {}", details.code(), details.message()));
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("UploadSkinError", e);
        } finally {
            skin.delete();
        }
        return Result.succeed(true);
    }
}
