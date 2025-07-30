package xyz.apleax.QinServer.SQL.Service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.noear.solon.annotation.Component;
import xyz.apleax.QinServer.SQL.Mapper.AccountMapper;
import xyz.apleax.QinServer.SQL.PO.Account;
import xyz.apleax.QinServer.SQL.Service.IAccountService;

/**
 * @author Apleax
 */
@Component
public class IAccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements IAccountService {
}