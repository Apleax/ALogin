package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Controller;
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
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.POJO.VerifyCodeKey;
import xyz.apleax.ALogin.Service.AccountService;
import xyz.apleax.ALogin.VO.*;

/**
 * 账号Controller
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@Mapping(path = "/api/account", produces = "application/json", consumes = "application/json")
public class Account {
    private final AccountService accountService;

    public Account(AccountService accountService) {
        this.accountService = accountService;
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Register/?{token}?", method = MethodType.POST,
            name = "注册", description = "注册接口，用于注册一个账号")
    public Result<SaTokenInfo> Register(@Validated RegisterVO registerVO, Context context, String token) {
        AccountBO accountBO = VOtoBOConvert.INSTANCE.registerVOToAccountBO(registerVO);
        String verify_code = registerVO.getVerify_code();
        return accountService.register(accountBO, verify_code, context.realIp(), token);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/VerifyCode", method = MethodType.POST,
            name = "验证码", description = "获取一个验证码发送到请求的邮箱")
    public Result<Long> VerifyCode(@Validated VerifyCodeVO verifyCodeVO) {
        VerifyCodeKey verifyCodeKey = null;
        if (verifyCodeVO instanceof RegisterVerifyCodeVO registerVerifyCodeVO)
            verifyCodeKey = new VerifyCodeKey(registerVerifyCodeVO.getEmail(), registerVerifyCodeVO.getType());
        if (verifyCodeVO instanceof ResetPasswordVerifyCodeVO resetPasswordVerifyCodeVO)
            verifyCodeKey = new VerifyCodeKey(resetPasswordVerifyCodeVO.getEmail(), resetPasswordVerifyCodeVO.getType());
        return accountService.verifyCode(verifyCodeKey);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Login/?{token}?", method = MethodType.POST,
            name = "登录", description = "登录接口，用于登录账号")
    public Result<SaTokenInfo> Login(@Validated LoginVO loginVO, String token) {
        LoginBO loginBO = null;
        if (loginVO instanceof LoginByEmailVO loginByEmailVO) {
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

    @SaIgnore
    @Transaction
    @Mapping(path = "/CheckToken", method = MethodType.POST,
            name = "校验Token", description = "校验Token并获取玩家配置")
    public GameProfile CheckToken(String token) {
        return accountService.checkToken(token);
    }

    @Transaction
    @Mapping(path = "/GetLoginInfo", method = {MethodType.GET, MethodType.POST},
            name = "获取登陆状态", description = "获取登陆状态接口")
    public Result<SaTokenInfo> GetLoginInfo() {
        return accountService.getLoginInfo();
    }

    @Transaction
    @Mapping(path = "/Logout", method = {MethodType.GET, MethodType.POST},
            name = "登出", description = "登出接口")
    public Result<SaTokenInfo> Logout() {
        return accountService.logout();
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
}
