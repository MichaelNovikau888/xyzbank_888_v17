package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.BranchDto;
import com.bank.publicinfo.entity.Branch;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface BranchMapper {
    BranchDto toDto(Branch e);
    @Mapping(target = "atms", ignore = true)
    Branch toEntity(BranchDto dto);
    @Mapping(target = "atms", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDto(BranchDto dto, @MappingTarget Branch e);
}
