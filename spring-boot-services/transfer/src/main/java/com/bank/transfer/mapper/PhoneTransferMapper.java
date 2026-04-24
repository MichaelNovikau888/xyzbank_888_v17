package com.bank.transfer.mapper;

import com.bank.transfer.dto.PhoneTransferDto;
import com.bank.transfer.entity.PhoneTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;


@Mapper(componentModel = "spring")
public interface PhoneTransferMapper {
    PhoneTransferMapper INSTANCE = Mappers.getMapper(PhoneTransferMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    PhoneTransfer toEntity(PhoneTransferDto dto);

    @Mapping(target = "phoneNumber", source = "phoneNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    PhoneTransferDto toDto(PhoneTransfer entity);
}
