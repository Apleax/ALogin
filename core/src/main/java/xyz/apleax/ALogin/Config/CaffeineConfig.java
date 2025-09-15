package xyz.apleax.ALogin.Config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Entity.POJO.AccountIndexCache;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.SQL.PO.AccountPO;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.RandomStringUtils;

import java.time.Duration;

/**
 * 所有的缓存配置
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public record CaffeineConfig(@Ds("DataBase") IAccountService accountService) implements LifecycleBean {
    public static final String CACHE_REMOVED_SIMPLE = "键 {} 被移除，值为 '{}'，原因：{}";

    private static void onRemoval(Object key, Object value, RemovalCause cause) {
        log.debug(CACHE_REMOVED_SIMPLE, key, value, cause);
    }

    @Override
    public void start() {
        log.info("Caching Components Loading Complete");
    }

    //验证码缓存
    @Bean(name = "VerifyCode", index = -100)
    public LoadingCache<@NotNull VerifyCodeKey, VerifyCodePOJO> VerifyCode() {
        return Caffeine.newBuilder()
                .removalListener(CaffeineConfig::onRemoval)
                .maximumSize(500)
                .initialCapacity(100)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats()
                .build(k -> {
                    if (accountService.count(new LambdaQueryWrapper<AccountPO>()
                            .eq(AccountPO::getEmail, k.email())) > 0) return null;
                    return new VerifyCodePOJO(RandomStringUtils.generateLowerUpper(6), null);
                });
    }

    // 账号缓存
    @Bean(name = "AccountCache", index = -100)
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
    @Bean(name = "AccountIndexCache", index = -100)
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
                        case MC_UUID -> {
                            if (Type.value().equals("NULL")) return null;
                            queryWrapper.eq(AccountPO::getMcUuid, Type.value());
                        }
                    }
                    if (!accountService.exists(queryWrapper)) return null;
                    return accountService.getOne(queryWrapper).getAccount();
                });
    }
}
