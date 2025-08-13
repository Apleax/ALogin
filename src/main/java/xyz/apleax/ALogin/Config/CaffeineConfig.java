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
import xyz.apleax.ALogin.Entity.PO.AccountPO;
import xyz.apleax.ALogin.Entity.POJO.AccountIndexCache;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Entity.POJO.VerifyCodePOJO;
import xyz.apleax.ALogin.Enum.AccountIndex;
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
                .build(accountService::getById);
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
                .build(Index -> {
                    AccountIndex accountIndex = Index.index();
                    switch (accountIndex) {
                        case ACCOUNT -> {
                            AccountPO account = accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                                    .select(AccountPO::getAccount)
                                    .eq(AccountPO::getAccount, Index.value()));
                            return account != null ? account.getAccount() : null;
                        }
                        case EMAIL -> {
                            AccountPO account = accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                                    .select(AccountPO::getAccount)
                                    .eq(AccountPO::getEmail, Index.value()));
                            return account != null ? account.getAccount() : null;
                        }
                        case QQ_ACCOUNT -> {
                            AccountPO account = accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                                    .select(AccountPO::getAccount)
                                    .eq(AccountPO::getQqAccount, Index.value()));
                            return account != null ? account.getAccount() : null;
                        }
                        case MC_UUID -> {
                            AccountPO account = accountService.getOne(new LambdaQueryWrapper<AccountPO>()
                                    .select(AccountPO::getAccount)
                                    .eq(AccountPO::getMcUuid, Index.value()));
                            return account != null ? account.getAccount() : null;
                        }
                        default -> {
                            return null;
                        }
                    }
                });
    }
}
