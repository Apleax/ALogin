package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.NotBlank;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.Entity.DTO.LoginByEmailDTO;
import xyz.apleax.ALogin.Entity.DTO.LoginByMcUuidDTO;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Service.AccountService;

/**
 * 账号Controller
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@Mapping(path = "/account", produces = "application/json", consumes = "application/json")
public class Account {
    private final AccountService accountService;

    public Account(AccountService accountService) {
        this.accountService = accountService;
    }

    @Mapping(path = "/Register", method = MethodType.POST,
            name = "注册", description = "注册接口，用于注册一个账号")
    public Result<SaTokenInfo> Register(@Validated RegisterDTO registerDTO,
                                        Context context) throws Exception {
        return accountService.register(registerDTO, context);
    }

    @Mapping(path = "/RegisterVerifyCode", method = MethodType.POST,
            name = "注册验证码", description = "获取一个用于注册的验证码发送到请求的邮箱")
    public Result<Long> VerifyCode(@Validated
                                   @NotBlank(message = "邮箱不能为空")
                                   @Email(message = "邮箱格式错误") String email) {
        return accountService.registerVerifyCode(email);
    }

    @Mapping(path = "/LoginByEmail", method = MethodType.POST,
            name = "通过邮箱登录", description = "登录接口，用于使用邮箱登录账号")
    public Result<SaTokenInfo> Login(@Validated LoginByEmailDTO loginByEmailDTO) throws Exception {
        return accountService.loginByEmail(loginByEmailDTO);
    }

    @Mapping(path = "/LoginByMcUuid", method = MethodType.POST,
            name = "通过Minecraft UUID登录", description = "登录接口，用于使用Minecraft UUID登录账号")
    public Result<SaTokenInfo> LoginByMcUuid(@Validated LoginByMcUuidDTO loginByMcUuidDTO) throws Exception {
        return accountService.loginByMcUuid(loginByMcUuidDTO);
    }
}
