package com.bank.account.mapper;

import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditMapper {
    AuditDto toAuditDto(Audit audit);

    Audit toAudit(AuditDto dto);
}
