package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.CertificateDto;
import com.bank.publicinfo.entity.Certificate;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface CertificateMapper {
    @Mapping(source = "bankDetails.id", target = "bankDetailsId")
    CertificateDto toDto(Certificate e);
    @Mapping(target = "bankDetails", ignore = true)
    Certificate toEntity(CertificateDto dto);
    @Mapping(target = "bankDetails", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDto(CertificateDto dto, @MappingTarget Certificate e);
}
