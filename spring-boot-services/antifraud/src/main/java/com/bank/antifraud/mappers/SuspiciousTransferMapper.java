package com.bank.antifraud.mappers;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.model.SuspiciousAccountTransfer;
import com.bank.antifraud.model.SuspiciousCardTransfer;
import com.bank.antifraud.model.SuspiciousPhoneTransfer;

import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface SuspiciousTransferMapper {
    SuspiciousAccountTransferDto toAccountDTO(SuspiciousAccountTransfer accountTransfer);

    SuspiciousPhoneTransferDto toPhoneDTO(SuspiciousPhoneTransfer accountTransfer);

    SuspiciousCardTransferDto toCardDTO(SuspiciousCardTransfer cardTransfer);

    SuspiciousCardTransfer toCardEntity(SuspiciousCardTransferDto cardTransferDto);

    SuspiciousPhoneTransfer toPhoneEntity(SuspiciousPhoneTransferDto phoneTransferDto);

    SuspiciousAccountTransfer toAccountEntity(SuspiciousAccountTransferDto accountTransferDto);
}
