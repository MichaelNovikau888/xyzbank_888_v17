package com.bank.antifraud.service;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.enums.FraudDecision;
import com.bank.antifraud.mappers.SuspiciousTransferMapper;
import com.bank.antifraud.metrics.AntifraudMetrics;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class SuspiciousTransferServiceImpl implements SuspiciousTransferService {

    /** Ниже этого порога — ALLOW. */
    private static final BigDecimal ALLOW_THRESHOLD    = new BigDecimal("10000.00");
    /** Выше этого порога — BLOCK. Между порогами — REVIEW. */
    private static final BigDecimal BLOCK_THRESHOLD    = new BigDecimal("50000.00");

    private final SuspiciousAccountTransferRepository accountTransferRepository;
    private final SuspiciousCardTransferRepository cardTransferRepository;
    private final SuspiciousPhoneTransferRepository phoneTransferRepository;
    private final SuspiciousTransferMapper mapper;
    private final AntifraudMetrics metrics;

    @Override
    @Transactional
    public SuspiciousCardTransferDto analyzeCardTransfer(BigDecimal amount, Integer transfer_id) {
        SuspiciousCardTransferDto dto = buildCardDto(amount, transfer_id);
        cardTransferRepository.save(mapper.toCardEntity(dto));

        metrics.getCardTransfersAnalyzed().increment();
        if (dto.isBlocked()) metrics.getCardTransfersBlocked().increment();
        return dto;
    }

    @Override
    @Transactional
    public SuspiciousPhoneTransferDto analyzePhoneTransfer(BigDecimal amount, Integer transfer_id) {
        SuspiciousPhoneTransferDto dto = buildPhoneDto(amount, transfer_id);
        phoneTransferRepository.save(mapper.toPhoneEntity(dto));

        metrics.getPhoneTransfersAnalyzed().increment();
        if (dto.isBlocked()) metrics.getPhoneTransfersBlocked().increment();
        return dto;
    }

    @Override
    @Transactional
    public SuspiciousAccountTransferDto analyzeAccountTransfer(BigDecimal amount, Integer transfer_id) {
        SuspiciousAccountTransferDto dto = buildAccountDto(amount, transfer_id);
        accountTransferRepository.save(mapper.toAccountEntity(dto));

        metrics.getAccountTransfersAnalyzed().increment();
        if (dto.isBlocked()) metrics.getAccountTransfersBlocked().increment();
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public SuspiciousPhoneTransferDto getPhoneTransfer(Integer id) {
        return mapper.toPhoneDTO(phoneTransferRepository.findById(id.longValue())
                .orElseThrow(() -> new RuntimeException("Phone transfer not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public SuspiciousCardTransferDto getCardTransfer(Integer id) {
        return mapper.toCardDTO(cardTransferRepository.findById(id.longValue())
                .orElseThrow(() -> new RuntimeException("Card transfer not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public SuspiciousAccountTransferDto getAccountTransfer(Integer id) {
        return mapper.toAccountDTO(accountTransferRepository.findById(id.longValue())
                .orElseThrow(() -> new RuntimeException("Account transfer not found: " + id)));
    }

    @Override
    @Transactional
    public void deletePhoneSuspiciousTransfer(Integer id) {
        phoneTransferRepository.deleteById(id.longValue());
    }

    @Override
    @Transactional
    public void deleteCardSuspiciousTransfer(Integer id) {
        cardTransferRepository.deleteById(id.longValue());
    }

    @Override
    @Transactional
    public void deleteAccountSuspiciousTransfer(Integer id) {
        accountTransferRepository.deleteById(id.longValue());
    }

    private SuspiciousCardTransferDto buildCardDto(BigDecimal amount, Integer id) {
        FraudDecision decision = resolveDecision(amount);
        boolean suspicious = decision != FraudDecision.ALLOW;
        boolean blocked    = decision == FraudDecision.BLOCK;
        String reason = decisionReason(decision, amount);

        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setCardTransferId(id);
        dto.setSuspicious(suspicious);
        dto.setBlocked(blocked);
        dto.setSuspiciousReason(reason);
        dto.setBlockedReason(blocked ? reason : "Not blocked");
        return dto;
    }

    private SuspiciousPhoneTransferDto buildPhoneDto(BigDecimal amount, Integer id) {
        FraudDecision decision = resolveDecision(amount);
        boolean suspicious = decision != FraudDecision.ALLOW;
        boolean blocked    = decision == FraudDecision.BLOCK;
        String reason = decisionReason(decision, amount);

        SuspiciousPhoneTransferDto dto = new SuspiciousPhoneTransferDto();
        dto.setPhoneTransferId(id);
        dto.setSuspicious(suspicious);
        dto.setBlocked(blocked);
        dto.setSuspiciousReason(reason);
        dto.setBlockedReason(blocked ? reason : "Not blocked");
        return dto;
    }

    private SuspiciousAccountTransferDto buildAccountDto(BigDecimal amount, Integer id) {
        FraudDecision decision = resolveDecision(amount);
        boolean suspicious = decision != FraudDecision.ALLOW;
        boolean blocked    = decision == FraudDecision.BLOCK;
        String reason = decisionReason(decision, amount);

        SuspiciousAccountTransferDto dto = new SuspiciousAccountTransferDto();
        dto.setAccountTransferId(id);
        dto.setSuspicious(suspicious);
        dto.setBlocked(blocked);
        dto.setSuspiciousReason(reason);
        dto.setBlockedReason(blocked ? reason : "Not blocked");
        return dto;
    }

 /**
     * Трёхуровневое решение по сумме:
     *   amount <= 10 000          → ALLOW
     *   10 000 < amount <= 50 000 → REVIEW (ручная проверка)
     *   amount > 50 000           → BLOCK
 */
    private FraudDecision resolveDecision(BigDecimal amount) {
        if (amount.compareTo(ALLOW_THRESHOLD) <= 0) return FraudDecision.ALLOW;
        if (amount.compareTo(BLOCK_THRESHOLD) <= 0) return FraudDecision.REVIEW;
        return FraudDecision.BLOCK;
    }

    private String decisionReason(FraudDecision decision, BigDecimal amount) {
        return switch (decision) {
            case ALLOW  -> "Amount " + amount + " is within normal range";
            case REVIEW -> "Amount " + amount + " exceeds review threshold (10 000)";
            case BLOCK  -> "Amount " + amount + " exceeds block threshold (50 000)";
        };
    }
}
