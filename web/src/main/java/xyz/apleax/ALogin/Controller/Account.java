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
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.NotBlank;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.ConvertMapper.VOtoBOConvert;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.Entity.BO.LoginBO;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.Service.AccountService;
import xyz.apleax.ALogin.VO.LoginByEmailVO;
import xyz.apleax.ALogin.VO.LoginByMcUuidVO;
import xyz.apleax.ALogin.VO.LoginVO;
import xyz.apleax.ALogin.VO.RegisterVO;

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
    @Mapping(path = "/Register", method = MethodType.POST,
            name = "注册", description = "注册接口，用于注册一个账号")
    public Result<SaTokenInfo> Register(@Validated RegisterVO registerVO, Context context) throws Exception {
        AccountBO accountBO = VOtoBOConvert.INSTANCE.registerVOToAccountBO(registerVO);
        String verify_code = registerVO.getVerify_code();
        return accountService.register(accountBO, verify_code, context.realIp());
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/RegisterVerifyCode", method = MethodType.POST,
            name = "注册验证码", description = "获取一个用于注册的验证码发送到请求的邮箱")
    public Result<Long> VerifyCode(@Validated
                                   @NotBlank(message = "邮箱不能为空")
                                   @Email(message = "邮箱格式错误") String email) {
        return accountService.registerVerifyCode(email);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Login", method = MethodType.POST,
            name = "登录", description = "登录接口，用于登录账号")
    public Result<SaTokenInfo> Login(@Validated LoginVO loginVO) throws Exception {
        LoginBO loginBO = null;
        AccountType accountType = null;
        String loginIp = null;
        if (loginVO instanceof LoginByEmailVO loginByEmailVO) {
            loginBO = VOtoBOConvert.INSTANCE.loginByEmailVOToLoginBO(loginByEmailVO);
            accountType = AccountType.EMAIL;
            loginIp = Context.current().realIp();
        }
        if (loginVO instanceof LoginByMcUuidVO loginByMcUuidVO) {
            loginBO = VOtoBOConvert.INSTANCE.loginByMcUuidVOToLoginBO(loginByMcUuidVO);
            accountType = AccountType.MC_UUID;
            loginIp = loginByMcUuidVO.getLogin_ip();
        }
        return accountService.login(loginBO, loginIp, accountType);
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/CheckLogin", method = MethodType.GET,
            name = "查询登陆状态", description = "查询登录状态接口")
    public Result<Boolean> CheckLogin() {
        return accountService.checkLogin();
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/GetLoginInfo", method = MethodType.GET,
            name = "获取登陆状态", description = "获取登陆状态接口")
    public Result<SaTokenInfo> GetLoginInfo() {
        return accountService.getLoginInfo();
    }

    @SaIgnore
    @Transaction
    @Mapping(path = "/Logout", method = MethodType.GET,
            name = "登出", description = "登出接口")
    public Result<SaTokenInfo> Logout() {
        return accountService.logout();
    }
}
