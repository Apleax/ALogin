package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempUtil;
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
import xyz.apleax.ALogin.Enum.LoginDevice;
import xyz.apleax.ALogin.Enum.VerifyCodeType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Service.Permission.PermissionService;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;
import xyz.apleax.ALogin.Service.Remote.AccountService;
import xyz.apleax.ALogin.Util.MailUtil;
import xyz.apleax.ALogin.VO.*;

import java.util.Map;
import java.util.UUID;
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
     * 内部调用密钥头（值 = Premium.assertion.secret）
     */
    private static final String KEY_HEADER = PremiumAssertionService.KEY_HEADER;

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9_-]+@[A-Za-z0-9]+(\\.[A-Za-z0-9-]+)+$");

    private final AccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PremiumAssertionService assertionService;
    private final PermissionService permissionService;

    public Account(AccountService accountService,
                   @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                   @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
                   PremiumAssertionService assertionService,
                   PermissionService permissionService) {
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.assertionService = assertionService;
        this.permissionService = permissionService;
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
        String token = ctx.param("token");
        return doLogin(loginVO, token == null || token.isBlank() ? null : token, ctx);
    }

    private Result<SaTokenInfo> doLogin(LoginVO loginVO, String token, Context ctx) {
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
        if (loginBO != null) {
            loginBO.setReal_ip(ctx.realIp());
            loginBO.setDevice(resolveDevice(token, ctx));
        }
        return accountService.login(loginBO, token);
    }

    /**
     * 判定本次登录的设备（终端）类型：只信服务端可验证的信号，不接受客户端自报。
     * <ul>
     *     <li>带<b>可解析的</b>进服会话临时 token（{@code POST /Login?token=...}）→ Java 客户端</li>
     *     <li>带内部密钥头（仅 Geyser 扩展持有 {@code X-ALogin-Key}）→ 基岩客户端</li>
     *     <li>其余 → 网页/前端</li>
     * </ul>
     * 临时 token 只判断"非空"不够：该参数完全由调用方控制，随便填一个字符串就能自报成 GAME 端，
     * 于是必须先用 {@link SaTempUtil} 解析，解析不出来才降级为网页端。
     */
    private LoginDevice resolveDevice(String gameToken, Context ctx) {
        if (gameToken != null && !gameToken.isBlank()) {
            if (SaTempUtil.parseToken(gameToken, UUID.class) != null) return LoginDevice.GAME;
            log.warn("进服临时 token 无法解析（已过期、已消费或伪造），本次按网页端处理");
        }
        String internalKey = ctx.header(KEY_HEADER);
        if (internalKey != null && !internalKey.isBlank()) {
            if (assertionService.matchesSharedKey(internalKey)) return LoginDevice.BEDROCK;
            // 密钥未配置（isEnabled()==false）或值不一致：基岩端会静默退化成网页端，留痕便于排查
            log.warn("内部密钥头 {} 校验未通过（Premium.assertion.secret 未配置或值不一致），本次按网页端处理", KEY_HEADER);
        }
        return LoginDevice.WEB;
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
            name = "刷新缓存", description = "清空账号、账号索引、权限与邮件模板缓存，保留验证码与登录会话")
    public Result<Boolean> RefreshCache() {
        StpUtil.checkLogin();
        String account = StpUtil.getLoginIdAsString();

        accountCache.invalidateAll();
        accountIndexCache.invalidateAll();
        permissionService.invalidateAll();
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
            // sessionToken 必须是本次请求的会话 token：不能按 account 反查"最后一个 token"（多端并存时会取错）
            if (token != null) Dami.bus().send("LoginEvent", Map.of(
                    "account", StpUtil.getLoginIdAsString(),
                    "token", token,
                    "sessionToken", StpUtil.getTokenValue()));
        }
        return Result.succeed(isLogin);
    }
}
