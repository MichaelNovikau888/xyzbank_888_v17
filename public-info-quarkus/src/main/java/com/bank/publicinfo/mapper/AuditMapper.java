package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.entity.Audit;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface AuditMapper {
    AuditDto toDto(Audit e);
    Audit toEntity(AuditDto dto);
}
