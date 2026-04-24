package com.bank.transfer.mapper;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.entity.AccountTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;



@Mapper(componentModel = "spring")
public interface AccountTransferMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", source = "accountNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    AccountTransfer toEntity(AccountTransferDto dto);

    @Mapping(target = "accountNumber", source = "accountNumber")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "purpose", source = "purpose")
    @Mapping(target = "accountDetailsId", source = "accountDetailsId")
    AccountTransferDto toDto(AccountTransfer entity);
}
