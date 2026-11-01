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
import xyz.apleax.ALogin.Config.AvatarConfig;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.Service.Remote.UserService;
import xyz.apleax.ALogin.TypeHandler.UuidTypeHandler;
import xyz.apleax.ALogin.Util.AvatarRenderer;
import xyz.apleax.ALogin.VO.UserInfoVO;

/**
 * 用户管理
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

    @SaIgnore
    @Mapping(path = "/Avatar", method = MethodType.GET,
            name = "获取玩家头像", description = "按 mcUuid 返回玩家头像 PNG；size(1-512)、overlay(true/false，是否绘制帽子层，缺省 false)")
    public void Avatar(String uuid, String size, String overlay, Context ctx) throws Throwable {
        if (uuid == null || uuid.isBlank()) {
            badRequest(ctx, "uuid 不能为空");
            return;
        }
        if (UuidTypeHandler.parse(uuid) == null) {
            badRequest(ctx, "uuid 格式非法");
            return;
        }
        int side = AvatarRenderer.DEFAULT_SIZE;
        if (size != null && !size.isBlank()) {
            try {
                side = AvatarRenderer.clampSize(Integer.parseInt(size.trim()));
            } catch (NumberFormatException e) {
                badRequest(ctx, "size 必须是 " + AvatarRenderer.MIN_SIZE + "-" + AvatarRenderer.MAX_SIZE + " 的整数");
                return;
            }
        }
        // overlay 是开关：只认 true/false（大小写不敏感），缺省或空值按 false；其它值直接报错，避免"看起来生效其实没有"
        boolean withOverlay = false;
        if (overlay != null && !overlay.isBlank()) {
            String value = overlay.trim();
            if ("true".equalsIgnoreCase(value)) withOverlay = true;
            else if (!"false".equalsIgnoreCase(value)) {
                badRequest(ctx, "overlay 只支持 true 或 false");
                return;
            }
        }
        // scale / default 参数不声明即被忽略
        byte[] png = userService.avatar(uuid, side, withOverlay);
        if (png == null) png = AvatarRenderer.placeholder(side);
        ctx.contentType("image/png");
        ctx.headerSet("Cache-Control", "public, max-age=" + AvatarConfig.cacheSeconds());
        ctx.output(png);
    }

    private static void badRequest(Context ctx, String message) throws Throwable {
        ctx.status(400);
        ctx.render(Result.failure(message));
    }

    @Transaction
    @Mapping(path = "/ChangeNickName", method = MethodType.PUT,
            name = "修改昵称", description = "修改昵称接口，用于修改昵称")
    public Result<Boolean> ChangeNickName(String nickname) {
        if (nickname == null || nickname.isEmpty()) return Result.failure("昵称不能为空", false);
        return userService.changeNickName(nickname);
    }

    /**
     * 校验Token并获取玩家配置；uuid 分支供服务器插件按 UUID 取皮肤数据，无会话凭证
     */
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
        else if (uuid != null && !uuid.isBlank()) profile = userService.GameProfile("", uuid, playerIp);
        if (profile == null) ctx.status(400);
        else ctx.status(200);
        return profile;
    }
}
