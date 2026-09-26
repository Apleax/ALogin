package xyz.apleax.ALogin.SQL.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.apleax.ALogin.PO.IdentityAssertionReplayPO;

/**
 * 外部身份断言防重放服务。
 *
 * @author Apleax
 */
public interface IIdentityAssertionReplayService extends IService<IdentityAssertionReplayPO> {
    boolean consumeOnce(String jti, long expiresAt);

    void removeExpired(long now);
}
