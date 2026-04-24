package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.entity.ATM;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface ATMMapper {
    @Mapping(source = "branch.id", target = "branchId")
    ATMDto toDto(ATM e);
    @Mapping(target = "branch", ignore = true)
    ATM toEntity(ATMDto dto);
    @Mapping(target = "branch", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDto(ATMDto dto, @MappingTarget ATM e);
}
