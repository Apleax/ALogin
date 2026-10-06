package xyz.apleax.ALogin.Service.Remote;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import org.noear.solon.core.handle.UploadedFile;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.VO.UserInfoVO;

/**
 * 用户服务契约（Dami LPC topic: user）
 *
 * @author Apleax
 */
@DamiTopic("user")
public interface UserService {
    /**
     * 当前登录用户信息
     */
    Result<UserInfoVO> getUserInfo();

    /**
     * 上传皮肤
     *
     * @param skin    皮肤文件
     * @param variant 皮肤类型（SLIM/CLASSIC）
     */
    Result<Boolean> uploadSkin(UploadedFile skin, String variant);

    /**
     * 修改昵称
     *
     * @param nickname 新昵称
     */
    Result<Boolean> changeNickName(String nickname);

    /**
     * 返回玩家档案
     *
     * @param token    Token
     * @param uuid     UUID
     * @param playerIp 代理上报的真实客户端 IP（可空，仅 token 分支生效）
     */
    GameProfile GameProfile(String token, String uuid, String playerIp);
}
