package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.entity.Audit;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-23T12:08:00+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.6 (Amazon.com Inc.)"
)
@Singleton
@Named
public class AuditMapperImpl implements AuditMapper {

    @Override
    public AuditDto toDto(Audit e) {
        if ( e == null ) {
            return null;
        }

        AuditDto auditDto = new AuditDto();

        auditDto.setId( e.getId() );
        auditDto.setEntityType( e.getEntityType() );
        auditDto.setOperationType( e.getOperationType() );
        auditDto.setCreatedBy( e.getCreatedBy() );
        auditDto.setModifiedBy( e.getModifiedBy() );
        auditDto.setCreatedAt( e.getCreatedAt() );
        auditDto.setModifiedAt( e.getModifiedAt() );
        auditDto.setNewEntityJson( e.getNewEntityJson() );
        auditDto.setEntityJson( e.getEntityJson() );

        return auditDto;
    }

    @Override
    public Audit toEntity(AuditDto dto) {
        if ( dto == null ) {
            return null;
        }

        Audit audit = new Audit();

        audit.setId( dto.getId() );
        audit.setEntityType( dto.getEntityType() );
        audit.setOperationType( dto.getOperationType() );
        audit.setCreatedBy( dto.getCreatedBy() );
        audit.setModifiedBy( dto.getModifiedBy() );
        audit.setCreatedAt( dto.getCreatedAt() );
        audit.setModifiedAt( dto.getModifiedAt() );
        audit.setNewEntityJson( dto.getNewEntityJson() );
        audit.setEntityJson( dto.getEntityJson() );

        return audit;
    }
}
