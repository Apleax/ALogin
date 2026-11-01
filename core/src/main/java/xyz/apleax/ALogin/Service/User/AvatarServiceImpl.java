package xyz.apleax.ALogin.Service.User;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.noear.dami2.solon.annotation.DamiTopic;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.PO.SkinPO;
import xyz.apleax.ALogin.SQL.Service.ISkinService;
import xyz.apleax.ALogin.TypeHandler.UuidTypeHandler;
import xyz.apleax.ALogin.Util.AvatarRenderer;

import java.util.UUID;

/**
 * 玩家头像：按 mcUuid 取皮肤并渲染头部正面。
 * <p>
 * 优先用 {@code skin.avatar} 里缓存的 16×8 规范化贴图；存量行为空、或贴图损坏时，现场由 {@code skin.skin_img}
 * 生成（不回写，避免读路径写库）。两者都不可用时回落到服务端配置的默认头像，**永不返回 null**。
 *
 * @author Apleax
 */
@Slf4j
@Managed
@DamiTopic("user")
public class AvatarServiceImpl {
    private final ISkinService skinService;
    private final AvatarDefaultProvider defaultProvider;

    public AvatarServiceImpl(@Ds("DataBase") ISkinService skinService,
                             AvatarDefaultProvider defaultProvider) {
        this.skinService = skinService;
        this.defaultProvider = defaultProvider;
    }

    /**
     * 渲染玩家头像 PNG
     *
     * @param uuid    服务器内 mcUuid（36 位带横线或 32 位无横线）
     * @param size    输出边长，越界裁剪到 1-512
     * @param overlay 是否叠加帽子层
     */
    public byte[] avatar(String uuid, int size, boolean overlay) {
        int side = AvatarRenderer.clampSize(size);
        UUID mcUuid = UuidTypeHandler.parse(uuid);
        if (mcUuid != null) {
            SkinPO skin = skinService.getOne(new LambdaQueryWrapper<SkinPO>()
                    .eq(SkinPO::getMcUuid, mcUuid));
            if (skin != null) {
                byte[] sheet = skin.getAvatar();
                if (sheet == null) sheet = AvatarRenderer.buildSheet(skin.getSkinImg());
                byte[] png = AvatarRenderer.render(sheet, side, overlay);
                if (png != null) return png;
            }
        }
        return defaultProvider.load(side);
    }
}
