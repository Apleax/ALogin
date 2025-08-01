package xyz.apleax.ALogin.SQL.Service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.noear.solon.annotation.Component;
import xyz.apleax.ALogin.SQL.Mapper.AccountMapper;
import xyz.apleax.ALogin.SQL.PO.Account;
import xyz.apleax.ALogin.SQL.Service.IAccountService;

/**
 * @author Apleax
 */
@Component
public class IAccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements IAccountService {
}