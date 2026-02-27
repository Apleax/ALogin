package xyz.apleax.ALogin.SQL.Service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.SQL.Mapper.AccountMapper;
import xyz.apleax.ALogin.SQL.Service.IAccountService;

/**
 * @author Apleax
 */
@Managed
public class IAccountServiceImpl extends ServiceImpl<AccountMapper, AccountPO> implements IAccountService {
}