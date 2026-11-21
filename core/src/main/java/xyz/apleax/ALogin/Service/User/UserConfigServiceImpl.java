package xyz.apleax.ALogin.Service.User;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.BindType;
import xyz.apleax.ALogin.Error.ApiException;
import xyz.apleax.ALogin.Error.ErrorCode;
import xyz.apleax.ALogin.PO.UserConfigPO;
import xyz.apleax.ALogin.SQL.Service.IUserConfigService;
import xyz.apleax.ALogin.VO.UserConfigVO;

/**
 * 用户配置的唯一入口：自动登录开关的读写都走这里。
 * <p>
 * 三处免密登录链路（正版自动登录、基岩版自动登录、进服 spawn 兜底）只调用
 * {@link #isAutoLoginEnabled(String, BindType)}，"无行即开启"的默认值语义与空值判断
 * 只在本类实现一次，新增配置项时不需要再改调用方。
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class UserConfigServiceImpl {
    private final IUserConfigService userConfigService;
    private final LoadingCache<@NotNull String, UserConfigPO> userConfigCache;

    public UserConfigServiceImpl(@Ds("DataBase") IUserConfigService userConfigService,
                                 @Inject("UserConfigCache") LoadingCache<@NotNull String, UserConfigPO> userConfigCache) {
        this.userConfigService = userConfigService;
        this.userConfigCache = userConfigCache;
    }

    /**
     * 某账号某类型的免密自动登录是否开启；无配置行按默认开启
     */
    public boolean isAutoLoginEnabled(String account, BindType type) {
        if (type == null) throw ApiException.of(ErrorCode.BIND_TYPE_INVALID);
        UserConfigPO cfg = userConfigCache.get(account);
        // null 视为开启：库里两列都是 NOT NULL，只有内存默认对象才可能出现 null
        return switch (type) {
            case JAVA -> !Boolean.FALSE.equals(cfg.getAutoLoginJava());
            case BEDROCK -> !Boolean.FALSE.equals(cfg.getAutoLoginBedrock());
        };
    }

    /**
     * 读取账号配置
     */
    public UserConfigVO getConfig(String account) {
        UserConfigPO cfg = userConfigCache.get(account);
        return new UserConfigVO(!Boolean.FALSE.equals(cfg.getAutoLoginJava()),
                !Boolean.FALSE.equals(cfg.getAutoLoginBedrock()));
    }

    /**
     * 设置指定账号的开关并返回最新配置。
     * <p>
     * 方法名与 Dami 契约方法 {@link #setAutoLogin(String, boolean)} 必须不同：Dami2 按方法名分发，
     * 同名重载会让提供方挑错方法（实测报 {@code IllegalArgumentException: argument type mismatch}），
     * 因此这里刻意不复用 {@code setAutoLogin} 这个名字。
     *
     * @param type 非法（null）抛 {@link ErrorCode#BIND_TYPE_INVALID}，入库失败抛 {@link ErrorCode#UPDATE_FAILED}
     */
    public UserConfigVO applyAutoLogin(String account, BindType type, boolean enabled) {
        if (type == null) throw ApiException.of(ErrorCode.BIND_TYPE_INVALID);
        // 必须从库里取（不能拿缓存里的默认对象）：upsert 判据依赖真实行的 id
        UserConfigPO userConfig = userConfigService.getOne(new LambdaQueryWrapper<UserConfigPO>()
                .eq(UserConfigPO::getAccount, account));
        if (userConfig == null) userConfig = UserConfigPO.defaults(account);
        switch (type) {
            case JAVA -> userConfig.setAutoLoginJava(enabled);
            case BEDROCK -> userConfig.setAutoLoginBedrock(enabled);
        }
        userConfig.setUpdateTime(System.currentTimeMillis());
        boolean saved;
        try {
            // 两列开关 + 账号 + 修改时间都有值，不会被 updateById 的 null 忽略影响
            saved = userConfig.getId() == null ? userConfigService.save(userConfig)
                    : userConfigService.updateById(userConfig);
        } catch (RuntimeException e) {
            log.error("保存用户配置异常，account={}, type={}, enabled={}", account, type, enabled, e);
            throw ApiException.of(ErrorCode.UPDATE_FAILED);
        }
        if (!saved) {
            log.error("保存用户配置失败，account={}, type={}, enabled={}", account, type, enabled);
            throw ApiException.of(ErrorCode.UPDATE_FAILED);
        }
        userConfigCache.invalidate(account);
        log.info("自动登录开关已更新，account={}, type={}, enabled={}", account, type, enabled);
        return getConfig(account);
    }

    // ========== Dami 契约方法：只做"取登录账号 + 委托" ==========

    /**
     * 当前登录用户的配置
     */
    public UserConfigVO getUserConfig() {
        return getConfig(StpUtil.getLoginIdAsString());
    }

    /**
     * 开关当前登录用户的自动登录
     *
     * @param type 绑定类型（java/bedrock 的 key）
     */
    public UserConfigVO setAutoLogin(String type, boolean enabled) {
        BindType bindType = BindType.getValueByKey(type);
        if (bindType == null) throw ApiException.of(ErrorCode.BIND_TYPE_INVALID);
        return applyAutoLogin(StpUtil.getLoginIdAsString(), bindType, enabled);
    }
}
