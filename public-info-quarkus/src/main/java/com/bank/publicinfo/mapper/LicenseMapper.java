package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.LicenseDto;
import com.bank.publicinfo.entity.License;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface LicenseMapper {
    @Mapping(source = "bankDetails.id", target = "bankDetailsId")
    LicenseDto toDto(License e);
    @Mapping(target = "bankDetails", ignore = true)
    License toEntity(LicenseDto dto);
    @Mapping(target = "bankDetails", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDto(LicenseDto dto, @MappingTarget License e);
}
