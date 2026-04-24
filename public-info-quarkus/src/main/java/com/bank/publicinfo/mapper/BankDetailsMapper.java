package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.entity.BankDetails;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface BankDetailsMapper {
    BankDetailsDto toDto(BankDetails e);
    @Mapping(target = "licenses",     ignore = true)
    @Mapping(target = "certificates", ignore = true)
    BankDetails toEntity(BankDetailsDto dto);
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "licenses",     ignore = true)
    @Mapping(target = "certificates", ignore = true)
    void updateFromDto(BankDetailsDto dto, @MappingTarget BankDetails e);
}
