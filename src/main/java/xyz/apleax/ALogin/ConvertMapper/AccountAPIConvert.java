package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.DTO.LoginByEmailDTO;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.PO.AccountPO;
import xyz.apleax.ALogin.Entity.PO.LoginPO;

/**
 * @author Apleax
 */
@Mapper
public interface AccountAPIConvert {
    AccountAPIConvert INSTANCE = Mappers.getMapper(AccountAPIConvert.class);

    AccountPO registerDTOToAccountPO(RegisterDTO registerDTO);

    LoginPO loginByEmailDTOToLoginPO(LoginByEmailDTO loginByEmailDTO);
}
