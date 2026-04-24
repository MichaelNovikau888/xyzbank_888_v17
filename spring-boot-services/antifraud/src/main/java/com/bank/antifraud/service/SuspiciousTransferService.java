package com.bank.antifraud.service;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;


import java.math.BigDecimal;

public interface SuspiciousTransferService {
    SuspiciousCardTransferDto analyzeCardTransfer(BigDecimal amount, Integer transfer_id);

    SuspiciousPhoneTransferDto analyzePhoneTransfer(BigDecimal amount, Integer transfer_id);

    SuspiciousAccountTransferDto analyzeAccountTransfer(BigDecimal amount, Integer transfer_id);

    SuspiciousPhoneTransferDto getPhoneTransfer(Integer id);

    SuspiciousCardTransferDto getCardTransfer(Integer id);

    SuspiciousAccountTransferDto getAccountTransfer(Integer id);

    void deletePhoneSuspiciousTransfer(Integer id);

    void deleteCardSuspiciousTransfer(Integer id);

    void deleteAccountSuspiciousTransfer(Integer id);

}
