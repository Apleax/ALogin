package xyz.apleax.ALogin.SQL.Service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.PO.AccountIdentityPO;
import xyz.apleax.ALogin.SQL.Mapper.AccountIdentityMapper;
import xyz.apleax.ALogin.SQL.Service.IAccountIdentityService;

/**
 * @author Apleax
 */
@Managed
public class IAccountIdentityServiceImpl
        extends ServiceImpl<AccountIdentityMapper, AccountIdentityPO>
        implements IAccountIdentityService {
}
