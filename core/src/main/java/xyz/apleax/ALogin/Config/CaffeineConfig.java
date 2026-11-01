package xyz.apleax.ALogin.Config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Service.User.AvatarDefaultProvider;
import xyz.apleax.ALogin.TypeHandler.UuidTypeHandler;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.awt.image.BufferedImage;
import java.time.Duration;

/**
 * 所有的缓存配置
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public record CaffeineConfig(@Ds("DataBase") IAccountService accountService) implements LifecycleBean {
    private static final String CACHE_REMOVED_SIMPLE = "键 {} 被移除，原因：{}";

    private static void onRemoval(Object key, Object value, RemovalCause cause) {
        log.debug(CACHE_REMOVED_SIMPLE, key, cause);
    }

    @Override
    public void start() {
        log.info("Caching Components Loading Complete");
    }

    //验证码缓存
    @Managed(name = "VerifyCode", index = -100)
    public LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> VerifyCode() {
        return Caffeine.newBuilder()
                .removalListener(CaffeineConfig::onRemoval)
                .maximumSize(500)
                .initialCapacity(100)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build(_ -> new VerifyCodePOJO(RandomStringUtils.generateLowerUpper(4), null));
    }

    // 账号缓存
    @Managed(name = "AccountCache", index = -100)
    public LoadingCache<@NotNull String, AccountPO> AccountCache() {
        return Caffeine.newBuilder()
                .removalListener(CaffeineConfig::onRemoval)
                .maximumSize(10_000)
                .initialCapacity(2000)
                .expireAfterAccess(Duration.ofHours(12))
                .refreshAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build(account -> accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                        .eq(AccountPO::getAccount, account)));
    }

    // 账号索引缓存
    @Managed(name = "AccountIndexCache", index = -100)
    public LoadingCache<@NotNull AccountIndexCache, String> AccountIndexCache() {
        return Caffeine.newBuilder()
                .removalListener(CaffeineConfig::onRemoval)
                .maximumSize(10_000)
                .initialCapacity(2000)
                .expireAfterAccess(Duration.ofHours(12))
                .refreshAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build(Type -> {
                    LambdaQueryWrapper<AccountPO> queryWrapper =
                            new LambdaQueryWrapper<AccountPO>()
                                    .select(AccountPO::getAccount);
                    switch (Type.index()) {
                        case ACCOUNT -> queryWrapper.eq(AccountPO::getAccount, Type.value());
                        case EMAIL -> queryWrapper.eq(AccountPO::getEmail, Type.value());
                        case QQ_ACCOUNT -> queryWrapper.eq(AccountPO::getQqAccount, Type.value());
                        case UUID -> queryWrapper.eq(AccountPO::getMcUuid, UuidTypeHandler.parse(Type.value()));
                        case PREMIUM_UUID ->
                                queryWrapper.eq(AccountPO::getPremiumUuid, UuidTypeHandler.parse(Type.value()));
                        case BEDROCK_XUID -> queryWrapper.eq(AccountPO::getBedrockXuid, Type.value());
                    }
                    AccountPO accountPO = accountService.getOne(queryWrapper);
                    return accountPO == null ? null : accountPO.getAccount();
                });
    }

    // 默认头像缓存：key = 配置的来源（Avatar.default-source）。加载失败返回 null，
    // Caffeine 不缓存 null，所以配置修好后下一次请求立即生效，也不会把失败结果缓存住。
    @Managed(name = "AvatarDefaultCache", index = -100)
    public LoadingCache<@NotNull String, BufferedImage> AvatarDefaultCache() {
        return Caffeine.newBuilder()
                .removalListener(CaffeineConfig::onRemoval)
                .maximumSize(4)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats()
                .build(AvatarDefaultProvider::resolve);
    }
}
