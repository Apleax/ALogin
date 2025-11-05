package xyz.apleax.ALogin.Controller;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.data.annotation.Transaction;
import org.noear.solon.validation.annotation.Valid;
import org.noear.solon.validation.annotation.Validated;
import xyz.apleax.ALogin.Util.RandomStringUtils;
import xyz.apleax.ALogin.VO.RegisterVO;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@Mapping(path = "/api/oauth2", produces = "application/json", consumes = "application/json")
public class OAuth2 {
    @Transaction
    @Mapping(path = "/CreatAuthorizationCode", method = MethodType.POST,
            name = "创建授权码", description = "创建一个授权码")
    public Result<String> creatAuthorizationCode(@Validated RegisterVO registerVO, Context context) {
        return Result.succeed(RandomStringUtils.generateLowerUpper(16));
    }
}
