package xyz.apleax.ALogin.Service.User;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import org.noear.solon.data.annotation.Transaction;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.PO.SkinPO;
import xyz.apleax.ALogin.POJO.GameProfile;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.SQL.Service.ISkinService;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * 皮肤写库事务
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class SkinPersistenceService {
    private final IAccountService accountService;
    private final ISkinService skinService;

    public SkinPersistenceService(@Ds("DataBase") IAccountService accountService,
                                  @Ds("DataBase") ISkinService skinService) {
        this.accountService = accountService;
        this.skinService = skinService;
    }

    /**
     * 在同一事务内写入皮肤与账号档案。
     *
     * @param account     账号
     * @param mcUuid      服务器内 UUID
     * @param skinUuid    MineSkin 返回的皮肤 UUID
     * @param skinData    皮肤图片字节
     * @param avatarSheet 头像贴图字节（16×8 PNG，由 skinData 生成；生成失败传 {@code null}）
     * @param properties  玩家档案
     */
    @Transaction
    public void writeSkinAndAccount(String account, UUID mcUuid, UUID skinUuid, byte[] skinData,
                                    byte[] avatarSheet, List<GameProfile.Property> properties) {
        SkinPO existingSkin = skinService.getOne(new LambdaQueryWrapper<SkinPO>()
                .eq(SkinPO::getMcUuid, mcUuid));

        // 显式 set：头像贴图生成失败时必须把库里的旧贴图一并置空，否则皮肤已换、头像还是旧的
        LambdaUpdateWrapper<SkinPO> updateWrapper = new LambdaUpdateWrapper<SkinPO>()
                .set(SkinPO::getSkinUuid, skinUuid)
                .set(SkinPO::getSkinImg, skinData)
                .set(SkinPO::getAvatar, avatarSheet)
                .eq(SkinPO::getMcUuid, mcUuid);
        boolean skinUpdated;
        if (existingSkin != null) {
            skinUpdated = skinService.update(updateWrapper);
        } else {
            SkinPO newSkin = new SkinPO();
            newSkin.setMcUuid(mcUuid);
            newSkin.setSkinUuid(skinUuid);
            newSkin.setSkinImg(skinData);
            newSkin.setAvatar(avatarSheet);
            try {
                skinUpdated = skinService.save(newSkin);
            } catch (RuntimeException e) {
                if (!isDuplicateKey(e)) throw e;
                log.warn("皮肤并发上传撞唯一键，回退为 update: mcUuid={}", mcUuid);
                skinUpdated = skinService.update(updateWrapper);
            }
        }

        AccountPO updateAccount = new AccountPO();
        updateAccount.setProperties(properties);
        boolean accountUpdated = accountService.update(updateAccount,
                new LambdaUpdateWrapper<AccountPO>().eq(AccountPO::getAccount, account));

        if (!skinUpdated || !accountUpdated)
            throw new IllegalStateException("皮肤数据写入失败, skinUpdated=" + skinUpdated
                    + ", accountUpdated=" + accountUpdated);
    }

    /**
     * 判断异常链中是否含 SQL 完整性约束冲突（SQLState 23xxx），跨方言通用。
     */
    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause())
            if (c instanceof SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if (state != null && state.startsWith("23")) return true;
            }
        return false;
    }
}
