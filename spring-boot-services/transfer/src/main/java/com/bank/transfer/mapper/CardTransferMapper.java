package com.bank.transfer.mapper;

import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.entity.CardTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CardTransferMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cardNumber", source = "cardNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    CardTransfer toEntity(CardTransferDto dto);

    @Mapping(target = "cardNumber", source = "cardNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    CardTransferDto toDto(CardTransfer entity);
}
