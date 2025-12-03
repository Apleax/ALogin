package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.BO.AccountBO;
import xyz.apleax.ALogin.Entity.BO.PermissionGroupBO;
import xyz.apleax.ALogin.SQL.PO.AccountPO;
import xyz.apleax.ALogin.SQL.PO.PermissionGroupPO;

/**
 * @author Apleax
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BOtoPOConvert {
    BOtoPOConvert INSTANCE = Mappers.getMapper(BOtoPOConvert.class);

    AccountPO registerBOToAccountPO(AccountBO accountBO);

    PermissionGroupPO permissionGroupBOToPermissionGroupPO(PermissionGroupBO permissionGroupBO);
}
