package xyz.apleax.ALogin.Controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.noear.solon.core.handle.Result;
import org.noear.solon.core.handle.UploadedFile;
import org.noear.solon.data.annotation.Transaction;
import org.noear.solon.validation.annotation.Valid;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.Service.UserService;
import xyz.apleax.ALogin.VO.UserInfoVO;

/**
 * 用户Controller
 *
 * @author Apleax
 */
@Slf4j
@Valid
@Controller
@AllArgsConstructor
@Mapping(path = "/api/web/user")
public class User {
    private final UserService userService;

    @Mapping(path = "/GetUserInfo", method = MethodType.GET,
            name = "获取用户信息", description = "获取用户信息接口，用于获取用户信息")

    public Result<UserInfoVO> GetUserInfo() {
        return userService.getUserInfo();
    }

    @Mapping(path = "/UploadSkin", method = MethodType.POST,
            name = "上传皮肤", description = "上传皮肤接口，用于更改皮肤")
    public Result<Boolean> UploadSkin(UploadedFile skin, String variant) {
        if (skin == null || skin.getContent() == null || variant == null || variant.isEmpty())
            return Result.failure("更新皮肤失败", false);
        return userService.uploadSkin(skin, variant);
    }

    @Transaction
    @Mapping(path = "/ChangeNickName", method = MethodType.PUT,
            name = "修改昵称", description = "修改昵称接口，用于修改昵称")
    public Result<Boolean> ChangeNickName(String nickname) {
        if (nickname == null || nickname.isEmpty()) return Result.failure("昵称不能为空", false);
        return userService.changeNickName(nickname);
    }

    @SaIgnore
    @Mapping(path = "/GameProfile", method = MethodType.POST,
            name = "校验Token", description = "校验Token并获取玩家配置")
    public GameProfile GameProfile(String token, String uuid, String playerIp, Context ctx) {
        if (token != null && uuid != null) {
            ctx.status(400);
            return null;
        }
        GameProfile profile = null;
        if (token != null && !token.isBlank()) profile = userService.GameProfile(token, "", playerIp);
        if (uuid != null && !uuid.isBlank()) profile = userService.GameProfile("", uuid, playerIp);
        if (profile == null) ctx.status(400);
        else ctx.status(200);
        return profile;
    }
}
