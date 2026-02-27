package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.BO.AccountBO;
import xyz.apleax.ALogin.BO.PermissionGroupBO;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.PO.PermissionGroupPO;

/**
 * @author Apleax
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BOtoPOConvert {
    BOtoPOConvert INSTANCE = Mappers.getMapper(BOtoPOConvert.class);

    AccountPO registerBOToAccountPO(AccountBO accountBO);

    PermissionGroupPO permissionGroupBOToPermissionGroupPO(PermissionGroupBO permissionGroupBO);
}
