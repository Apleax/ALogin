package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.Dami;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Transaction;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.BO.LoginBO;
import xyz.apleax.ALogin.ConvertMapper.VOtoBOConvert;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Service.Remote.AccountService;
import xyz.apleax.ALogin.Util.MailUtil;
import xyz.apleax.ALogin.VO.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 账号管理
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@Mapping(path = "/api/web/account", produces = "application/json", consumes = "application/json")
public class Account {
    /**
     * 缓存刷新特权账号（临时过渡，后续接入外部权限系统后替换）
     */
    private static final String CACHE_REFRESH_ACCOUNT = "81271059";

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9_-]+@[A-Za-z0-9]+(\\.[A-Za-z0-9-]+)+$");

    private final AccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;

    public Account(AccountService accountService,
                   @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                   @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Register", method = MethodType.POST,
            name = "注册", description = "注册接口，用于注册一个账号")
    public Result<String> Register(@Validated RegisterVO registerVO, Context context) {
        AccountBO accountBO = VOtoBOConvert.INSTANCE.registerVOToAccountBO(registerVO);
        String verify_code = registerVO.getVerify_code();
        return accountService.register(accountBO, verify_code, context.realIp());
    }

    @SaIgnore
    @Mapping(path = "/VerifyCode", method = MethodType.POST,
            name = "验证码", description = "获取一个验证码发送到请求的邮箱")
    public Result<Long> VerifyCode(@Validated VerifyCodeVO verifyCodeVO) {
        if (verifyCodeVO == null) return Result.failure("请求参数缺失");
        VerifyCodeKey verifyCodeKey = null;
        if (verifyCodeVO instanceof RegisterVerifyCodeVO registerVerifyCodeVO) {
            if (!EMAIL_REGEX.matcher(registerVerifyCodeVO.getEmail()).matches()) return Result.failure("邮箱格式错误");
            verifyCodeKey = new VerifyCodeKey(registerVerifyCodeVO.getEmail(), VerifyCodeType.REGISTER);
        }
        if (verifyCodeVO instanceof ResetPasswordVerifyCodeVO resetPasswordVerifyCodeVO) {
            if (!EMAIL_REGEX.matcher(resetPasswordVerifyCodeVO.getEmail()).matches())
                return Result.failure("邮箱格式错误");
            verifyCodeKey = new VerifyCodeKey(resetPasswordVerifyCodeVO.getEmail(), VerifyCodeType.RESET_PASSWORD);
        }
        if (verifyCodeKey == null) return Result.failure("请求参数缺失");
        return accountService.verifyCode(verifyCodeKey);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Login", method = MethodType.POST,
            name = "登录", description = "登录接口，用于登录账号")
    public Result<SaTokenInfo> Login(@Validated LoginVO loginVO, Context ctx) {
        return doLogin(loginVO, tokenOf(ctx));
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Login/**", method = MethodType.POST,
            name = "登录", description = "登录接口，用于登录账号")
    public Result<SaTokenInfo> LoginWithToken(@Validated LoginVO loginVO, Context ctx) {
        return doLogin(loginVO, tokenOf(ctx));
    }

    private Result<SaTokenInfo> doLogin(LoginVO loginVO, String token) {
        LoginBO loginBO = null;
        if (loginVO instanceof LoginByEmailVO loginByEmailVO) {
            if (!EMAIL_REGEX.matcher(loginByEmailVO.getEmail()).matches()) return Result.failure("邮箱格式错误");
            loginBO = VOtoBOConvert.INSTANCE.loginByEmailVOToLoginBO(loginByEmailVO);
            loginBO.setAccount_type(AccountType.EMAIL);
        }
        if (loginVO instanceof LoginByAccountVO loginByAccountVO) {
            loginBO = VOtoBOConvert.INSTANCE.loginByAccountVOToLoginBO(loginByAccountVO);
            loginBO.setAccount_type(AccountType.ACCOUNT);
        }
        if (loginBO != null) loginBO.setReal_ip(Context.current().realIp());
        return accountService.login(loginBO, token);
    }

    /**
     * 提取进服会话 token：优先查询参数，其次路径尾段（/Login/{token} 与 /Login/ 兼容）
     */
    private static String tokenOf(Context ctx) {
        String token = ctx.param("token");
        if (token != null && !token.isBlank()) return token;
        String path = ctx.pathNew();
        int idx = path.lastIndexOf("/Login/");
        if (idx >= 0 && idx + 7 < path.length()) {
            String tail = path.substring(idx + 7);
            if (!tail.isBlank() && tail.indexOf('/') < 0) return tail;
        }
        return null;
    }

    @Mapping(path = "/Logout", method = {MethodType.GET, MethodType.POST},
            name = "登出", description = "登出接口")
    public Result<Void> Logout() {
        return accountService.logout(null);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/ResetPassword", method = MethodType.POST,
            name = "重置密码", description = "重置密码接口")
    public Result<Boolean> ResetPassword(@Validated ResetPasswordVO resetPasswordVO) {
        return accountService.resetPassword(resetPasswordVO.getEmail(),
                resetPasswordVO.getVerify_code(),
                resetPasswordVO.getNew_password());
    }

    @Mapping(path = "/RefreshCache", method = MethodType.POST,
            name = "刷新缓存", description = "清空账号、账号索引和邮件模板缓存，保留验证码与登录会话")
    public Result<Boolean> RefreshCache(Context context) {
        StpUtil.checkLogin();
        String account = StpUtil.getLoginIdAsString();
        if (!CACHE_REFRESH_ACCOUNT.equals(account)) {
            context.status(403);
            return Result.failure("无权刷新缓存", false);
        }

        accountCache.invalidateAll();
        accountIndexCache.invalidateAll();
        MailUtil.clearTemplateCache();
        log.info("业务缓存刷新成功，account={}", account);
        return Result.succeed(true);
    }

    @SaIgnore
    @Mapping(path = "IsLogin", method = MethodType.ALL,
            name = "是否登录", description = "检查是否登录")
    public Result<Boolean> IsLogin(String token) {
        boolean isLogin = StpUtil.isLogin();
        if (isLogin) {
            StpUtil.updateLastActiveToNow();
            if (token != null)
                Dami.bus().send("LoginEvent", Map.of("account", StpUtil.getLoginIdAsString(), "token", token));
        }
        return Result.succeed(isLogin);
    }
}
