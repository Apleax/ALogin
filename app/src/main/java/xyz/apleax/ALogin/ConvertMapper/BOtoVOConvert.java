package xyz.apleax.ALogin.ConvertMapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import xyz.apleax.ALogin.Entity.BO.GameProfileBO;
import xyz.apleax.ALogin.VO.GameProfileVO;

/**
 * @author Apleax
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BOtoVOConvert {
    BOtoVOConvert INSTANCE = Mappers.getMapper(BOtoVOConvert.class);

    GameProfileVO gameProfileBOToGameProfileVO(GameProfileBO gameProfileBO);
}

