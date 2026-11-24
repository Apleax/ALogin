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
 * 用户配置入口（自动登录开关等）
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
     * 设置指定账号的开关并返回最新配置
     */
    public UserConfigVO applyAutoLogin(String account, BindType type, boolean enabled) {
        if (type == null) throw ApiException.of(ErrorCode.BIND_TYPE_INVALID);
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
