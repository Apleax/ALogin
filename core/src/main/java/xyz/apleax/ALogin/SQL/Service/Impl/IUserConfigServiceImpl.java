package xyz.apleax.ALogin.SQL.Service.Impl;

import com.baomidou.mybatisplus.solon.service.impl.ServiceImpl;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.PO.UserConfigPO;
import xyz.apleax.ALogin.SQL.Mapper.UserConfigMapper;
import xyz.apleax.ALogin.SQL.Service.IUserConfigService;

/**
 *
 *
 * @author Apleax
 */
@Managed
public class IUserConfigServiceImpl extends ServiceImpl<UserConfigMapper, UserConfigPO> implements IUserConfigService {
}
