package xyz.apleax.ALogin.Service;

import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.core.handle.Result;
import org.noear.solon.core.handle.UploadedFile;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.VO.UserInfoVO;

/**
 *
 *
 * @author Apleax
 */
@DamiTopic("user")
public interface UserService {
    Result<UserInfoVO> getUserInfo();

    Result<Boolean> uploadSkin(UploadedFile skin, String variant);

    Result<Boolean> changeNickName(String nickname);

    /**
     * 返回玩家档案
     *
     * @param token Token
     * @return GameProfile
     */
    GameProfile GameProfile(String token, String uuid);
}
