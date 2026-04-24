package com.bank.transfer.service;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;

public interface TransferService {
    void saveAccountTransfer(AccountTransferDto dto);
    void saveCardTransfer(CardTransferDto dto);
    void savePhoneTransfer(PhoneTransferDto dto);
}