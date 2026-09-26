package xyz.apleax.ALogin.SQL.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.apleax.ALogin.PO.IdentityAssertionReplayPO;

/**
 * 外部身份断言防重放数据访问层。
 *
 * @author Apleax
 */
@Mapper
public interface IdentityAssertionReplayMapper extends BaseMapper<IdentityAssertionReplayPO> {
}
