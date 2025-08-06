package xyz.apleax.ALogin.Controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.validation.annotation.Email;
import org.noear.solon.validation.annotation.NotEmpty;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.VO.RegisterVO;
import xyz.apleax.ALogin.Service.AccountService;

/**
 * 账号Controller
 *
 * @author Apleax
 */
@Controller
@Mapping(path = "/account", produces = "application/json", consumes = "application/json")
@Valid
public class Account {
    @Inject
    AccountService accountService;

    @Mapping(path = "/Register", method = MethodType.POST,
            name = "注册", description = "注册接口，用于注册一个账号")
    public Result<RegisterVO> Register(@Validated RegisterDTO registerDTO) throws Exception {
        return accountService.register(registerDTO);
    }

    @Mapping(path = "/VerifyCode", method = MethodType.POST,
            name = "验证码", description = "获取验证码，发送一个验证码到请求的邮箱")
    public Result<Long> VerifyCode(@Validated
                                   @NotEmpty(message = "邮箱不能为空")
                                   @Email(message = "邮箱格式错误") String email) {
        return accountService.emailVerifyCode(email);
    }
}
