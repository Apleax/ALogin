package xyz.apleax.ALogin.SQL.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.apleax.ALogin.PO.AccountIdentityPO;

/**
 * 外部身份绑定数据访问层。
 *
 * @author Apleax
 */
@Mapper
public interface AccountIdentityMapper extends BaseMapper<AccountIdentityPO> {
}
