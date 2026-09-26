package xyz.apleax.ALogin.SQL.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.PO.IdentityAssertionReplayPO;
import xyz.apleax.ALogin.SQL.Mapper.IdentityAssertionReplayMapper;
import xyz.apleax.ALogin.SQL.Service.IIdentityAssertionReplayService;

/**
 * @author Apleax
 */
@Managed
public class IIdentityAssertionReplayServiceImpl
        extends ServiceImpl<IdentityAssertionReplayMapper, IdentityAssertionReplayPO>
        implements IIdentityAssertionReplayService {
    @Override
    public boolean consumeOnce(String jti, long expiresAt) {
        try {
            return save(new IdentityAssertionReplayPO(null, jti, expiresAt, System.currentTimeMillis()));
        } catch (RuntimeException exception) {
            if (getBaseMapper().selectCount(new LambdaQueryWrapper<IdentityAssertionReplayPO>()
                    .eq(IdentityAssertionReplayPO::getJti, jti)) > 0) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public void removeExpired(long now) {
        remove(new LambdaQueryWrapper<IdentityAssertionReplayPO>()
                .lt(IdentityAssertionReplayPO::getExpiresAt, now));
    }
}
