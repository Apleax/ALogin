package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.Entity.BO.LoginBO;
import xyz.apleax.ALogin.VO.LoginByEmailVO;
import xyz.apleax.ALogin.VO.LoginByMcUuidVO;
import xyz.apleax.ALogin.VO.RegisterVO;

/**
 * @author Apleax
 */
@Mapper
public interface VOtoBOConvert {
    VOtoBOConvert INSTANCE = Mappers.getMapper(VOtoBOConvert.class);

    AccountBO registerVOToAccountBO(RegisterVO registerVO);

    LoginBO loginByEmailVOToLoginBO(LoginByEmailVO loginByEmailVO);

    LoginBO loginByMcUuidVOToLoginBO(LoginByMcUuidVO loginByMcUuidVO);
}
