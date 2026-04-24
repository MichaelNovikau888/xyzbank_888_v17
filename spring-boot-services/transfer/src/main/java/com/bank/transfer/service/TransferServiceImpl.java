package com.bank.transfer.service;

import com.bank.transfer.antifraud.AntifraudOutboxHelper;
import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;
import com.bank.transfer.entity.AccountTransfer;
import com.bank.transfer.entity.CardTransfer;
import com.bank.transfer.entity.PhoneTransfer;
import com.bank.transfer.enums.TransferStatus;
import com.bank.transfer.exception.KafkaErrorPublisher;
import com.bank.transfer.mapper.AccountTransferMapper;
import com.bank.transfer.mapper.CardTransferMapper;
import com.bank.transfer.mapper.PhoneTransferMapper;
import com.bank.transfer.metrics.TransferMetrics;
import com.bank.transfer.notification.TransferNotificationOutboxHelper;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final AccountTransferRepository      accountTransferRepository;
    private final CardTransferRepository         cardTransferRepository;
    private final PhoneTransferRepository        phoneTransferRepository;
    private final AccountTransferMapper          accountTransferMapper;
    private final CardTransferMapper             cardTransferMapper;
    private final PhoneTransferMapper            phoneTransferMapper;
    private final KafkaErrorPublisher            errorPublisher;
    private final TransferMetrics                metrics;
    private final AntifraudOutboxHelper          antifraudOutboxHelper;
    private final TransferNotificationOutboxHelper notificationOutboxHelper;

    // ── Account transfer ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveAccountTransfer(AccountTransferDto dto) {
        try {
            if (dto == null) throw new IllegalArgumentException("AccountTransferDto cannot be null");

            if (dto.getAccountNumber() != null && dto.getAccountDetailsId() != null
                    && dto.getAmount() != null
                    && accountTransferRepository.existsByAccountNumberAndAccountDetailsIdAndAmount(
                    dto.getAccountNumber(), dto.getAccountDetailsId(), dto.getAmount())) {
                log.warn("Idempotency: account transfer already exists accountNumber={}", dto.getAccountNumber());
                metrics.getAccountTransferSkipped().increment();
                return;
            }

            // Устанавливаем начальный статус и clientId перед сохранением
            AccountTransfer entity = accountTransferMapper.toEntity(dto);
            entity.setStatus(TransferStatus.CREATED);
            entity.setClientId(dto.getClientId());

            AccountTransfer saved = accountTransferRepository.save(entity);
            // Outbox 1: transfer.antifraud.check
            antifraudOutboxHelper.enqueueAccountTransferCheck(
                    saved.getId(), saved.getAmount(), saved.getPurpose());

            // Outbox 3: transfer.notification — push «Перевод создан»
            notificationOutboxHelper.enqueueCreated(
                    saved.getId(), saved.getClientId(),
                    "ACCOUNT", saved.getAmount(), "RUB",
                    saved.getAccountNumber(),
                    saved.getPurpose());

            metrics.getAccountTransferSaved().increment();
            if (dto.getAmount() != null) metrics.getTransferAmountSummary().record(dto.getAmount().doubleValue());
            log.info("Account transfer saved + notifications enqueued: ID={}", saved.getId());

        } catch (Exception e) {
            metrics.getTransferFailed().increment();
            log.error("Failed to save account transfer: {}", e.getMessage());
            errorPublisher.publish(e, null);
            throw new RuntimeException("Failed to save account transfer", e);
        }
    }

    // ── Card transfer ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveCardTransfer(CardTransferDto dto) {
        try {
            if (dto == null) throw new IllegalArgumentException("CardTransferDto cannot be null");

            if (dto.getCardNumber() != null && dto.getAccountDetailsId() != null
                    && dto.getAmount() != null
                    && cardTransferRepository.existsByCardNumberAndAccountDetailsIdAndAmount(
                    dto.getCardNumber(), dto.getAccountDetailsId(), dto.getAmount())) {
                log.warn("Idempotency: card transfer already exists cardNumber={}", dto.getCardNumber());
                metrics.getCardTransferSkipped().increment();
                return;
            }

            CardTransfer entity = cardTransferMapper.toEntity(dto);
            entity.setStatus(TransferStatus.CREATED);
            entity.setClientId(dto.getClientId());

            CardTransfer saved = cardTransferRepository.save(entity);

            antifraudOutboxHelper.enqueueCardTransferCheck(
                    saved.getId(), saved.getAmount(), saved.getPurpose());

            // Маскируем номер карты: **** **** **** 1234
            String maskedCard = maskCardNumber(saved.getCardNumber());
            notificationOutboxHelper.enqueueCreated(
                    saved.getId(), saved.getClientId(),
                    "CARD", saved.getAmount(), "RUB",
                    maskedCard,
                    saved.getPurpose());

            metrics.getCardTransferSaved().increment();
            if (dto.getAmount() != null) metrics.getTransferAmountSummary().record(dto.getAmount().doubleValue());
            log.info("Card transfer saved + notifications enqueued: ID={}", saved.getId());

        } catch (Exception e) {
            metrics.getTransferFailed().increment();
            log.error("Failed to save card transfer: {}", e.getMessage());
            errorPublisher.publish(e, null);
            throw new RuntimeException("Failed to save card transfer", e);
        }
    }

    // ── Phone transfer ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void savePhoneTransfer(PhoneTransferDto dto) {
        try {
            if (dto == null) throw new IllegalArgumentException("PhoneTransferDto cannot be null");

            if (dto.getPhoneNumber() != null && dto.getAccountDetailsId() != null
                    && dto.getAmount() != null
                    && phoneTransferRepository.existsByPhoneNumberAndAccountDetailsIdAndAmount(
                    dto.getPhoneNumber(), dto.getAccountDetailsId(), dto.getAmount())) {
                log.warn("Idempotency: phone transfer already exists phoneNumber={}", dto.getPhoneNumber());
                metrics.getPhoneTransferSkipped().increment();
                return;
            }

            PhoneTransfer entity = phoneTransferMapper.toEntity(dto);
            entity.setStatus(TransferStatus.CREATED);
            entity.setClientId(dto.getClientId());

            PhoneTransfer saved = phoneTransferRepository.save(entity);

            antifraudOutboxHelper.enqueuePhoneTransferCheck(
                    saved.getId(), saved.getAmount(), saved.getPurpose());

            // Маскируем телефон: +7 *** *** ** **
            String maskedPhone = maskPhoneNumber(saved.getPhoneNumber());
            notificationOutboxHelper.enqueueCreated(
                    saved.getId(), saved.getClientId(),
                    "PHONE", saved.getAmount(), "RUB",
                    maskedPhone,
                    saved.getPurpose());

            metrics.getPhoneTransferSaved().increment();
            if (dto.getAmount() != null) metrics.getTransferAmountSummary().record(dto.getAmount().doubleValue());
            log.info("Phone transfer saved + notifications enqueued: ID={}", saved.getId());

        } catch (Exception e) {
            metrics.getTransferFailed().increment();
            log.error("Failed to save phone transfer: {}", e.getMessage());
            errorPublisher.publish(e, null);
            throw new RuntimeException("Failed to save phone transfer", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

 /**
     * Маскировка номера карты: оставляем последние 4 цифры.
     * 1234567890123456 → **** **** **** 3456
 */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null) return "****";
        String s = cardNumber;
        if (s.length() < 4) return "****";
        return "**** **** **** " + s.substring(s.length() - 4);
    }

 /**
     * Маскировка телефона: оставляем первые 2 и последние 2 цифры.
     * 79001234567 → +7 9** *** ** 67
 */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "+7 ***";
        String s = phoneNumber;
        if (s.length() < 11) return "+" + s;
        return "+" + s.charAt(0) + " " + s.charAt(1) + "** *** ** " + s.substring(s.length() - 2);
    }
}
