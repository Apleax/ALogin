package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.SQL.PO.AccountPO;

/**
 * @author Apleax
 */
@Mapper
public interface BOtoPOConvert {
    BOtoPOConvert INSTANCE = Mappers.getMapper(BOtoPOConvert.class);

    AccountPO registerBOToAccountPO(AccountBO accountBO);
}
