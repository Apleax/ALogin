package xyz.apleax.ALogin.SQL.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.apleax.ALogin.SQL.PO.AccountPO;

/**
 * @author Apleax
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountPO> {
}
