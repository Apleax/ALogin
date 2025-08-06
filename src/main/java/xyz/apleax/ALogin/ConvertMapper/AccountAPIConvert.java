package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.DTO.RegisterDTO;
import xyz.apleax.ALogin.Entity.PO.AccountPO;

/**
 * @author Apleax
 */
@Mapper
public interface AccountAPIConvert {
    AccountAPIConvert INSTANCE = Mappers.getMapper(AccountAPIConvert.class);

    AccountPO registerDTOToAccountPO(RegisterDTO registerDTO);
}
