package com.bank.antifraud.kafkaConsumer;

import com.bank.antifraud.dto.AntifraudRequestEvent;
import com.bank.antifraud.dto.AntifraudResponseEvent;
import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.enums.FraudDecision;
import com.bank.antifraud.kafkaProducer.SuspiciousTransferProducer;
import com.bank.antifraud.metrics.AntifraudMetrics;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import com.bank.antifraud.service.SuspiciousTransferServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka-консьюмер антифрод-проверок.
 */
 /**
 * Два слушателя:
 */
 /**
 * 1. handlePaymentAntifraudCheck  — от payment-api
 *    Входящий:  payment.antifraud.check
 *    Исходящий: payment.antifraud.response
 */
 /**
 * 2. handleTransferAntifraudCheck — от transfer-service  ← НОВЫЙ
 *    Входящий:  transfer.antifraud.check
 *    Исходящий: transfer.antifraud.response
 */
 /**
 * Логика анализа одинакова для обоих потоков (те же пороги, те же репозитории).
 * Различаются только: DTO запроса/ответа, топики, factory.
 */
 /**
 * Idempotency:
 *   Partition key = transferId/paymentId → два сообщения с одним id
 *   всегда в одной партиции → race condition исключена.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuspiciousTransferConsumer {

    private final SuspiciousTransferServiceImpl       transferService;
    private final SuspiciousTransferProducer          kafkaProducer;
    private final SuspiciousCardTransferRepository    cardRepo;
    private final SuspiciousPhoneTransferRepository   phoneRepo;
    private final SuspiciousAccountTransferRepository accountRepo;
    private final AntifraudMetrics                    metrics;

    // ── 1. payment-api → payment.antifraud.check ──────────────────────────────

    @KafkaListener(
            topics = "payment.antifraud.check",
            groupId = "anti_fraud-group",
            containerFactory = "antifraudRequestListenerContainerFactory")
    public void handlePaymentAntifraudCheck(@Payload AntifraudRequestEvent request) {

        log.info("Payment antifraud check: paymentId={} amount={} type={}",
                request.getPaymentId(), request.getAmount(), request.getTransferType());

        try {
            AntifraudResponseEvent response = analyzePayment(request);
            kafkaProducer.sendAntifraudResponse(response);
            log.info("Payment antifraud decision: paymentId={} decision={} riskScore={}",
                    request.getPaymentId(), response.getDecision(), response.getRiskScore());

        } catch (Exception e) {
            log.error("Error during payment antifraud check paymentId={}: {}",
                    request.getPaymentId(), e.getMessage(), e);
            kafkaProducer.sendAntifraudResponse(AntifraudResponseEvent.builder()
                    .paymentId(request.getPaymentId())
                    .transferType(request.getTransferType())
                    .decision(FraudDecision.REVIEW)
                    .reason("Antifraud analysis error: " + e.getMessage())
                    .riskScore(50)
                    .build());
        }
    }

    // ── 2. transfer-service → transfer.antifraud.check ───────────────────────

 /**
     * Входящий топик: transfer.antifraud.check
     * Тип payload:    TransferAntifraudRequestEvent (com.bank.antifraud.dto.TransferAntifraudRequestEvent)
 */
 /**
     * Повторно использует ту же логику analyse(), но DTO другой:
     * - transferId вместо paymentId
     * - ответ идёт в transfer.antifraud.response
 */
    @KafkaListener(
            topics = "transfer.antifraud.check",
            groupId = "anti_fraud-transfer-group",
            containerFactory = "antifraudTransferCheckListenerContainerFactory")
    public void handleTransferAntifraudCheck(
            @Payload com.bank.antifraud.dto.TransferAntifraudRequestEvent request) {

        log.info("Transfer antifraud check: transferId={} amount={} type={}",
                request.getTransferId(), request.getAmount(), request.getTransferType());

        try {
            com.bank.antifraud.dto.TransferAntifraudResponseEvent response =
                    analyzeTransfer(request);
            kafkaProducer.sendTransferAntifraudResponse(response);
            log.info("Transfer antifraud decision: transferId={} decision={} riskScore={}",
                    request.getTransferId(), response.getDecision(), response.getRiskScore());

        } catch (Exception e) {
            log.error("Error during transfer antifraud check transferId={}: {}",
                    request.getTransferId(), e.getMessage(), e);
            kafkaProducer.sendTransferAntifraudResponse(
                    com.bank.antifraud.dto.TransferAntifraudResponseEvent.builder()
                            .transferId(request.getTransferId())
                            .transferType(request.getTransferType())
                            .decision("REVIEW")
                            .reason("Antifraud analysis error: " + e.getMessage())
                            .riskScore(50)
                            .build());
        }
    }

    // ── private: анализ для payment-api (AntifraudRequestEvent) ──────────────

    private AntifraudResponseEvent analyzePayment(AntifraudRequestEvent request) {
        Integer internalId = request.getPaymentId().intValue();
        String type = request.getTransferType() == null ? "ACCOUNT" : request.getTransferType();

        return switch (type) {
            case "CARD" -> {
                if (cardRepo.findByCardTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: card paymentId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildPaymentResponseFromCard(request, internalId);
                }
                SuspiciousCardTransferDto dto =
                        transferService.analyzeCardTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getCardTransfersBlocked().increment();
                yield toPaymentResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
            case "PHONE" -> {
                if (phoneRepo.findByPhoneTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: phone paymentId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildPaymentResponseFromPhone(request, internalId);
                }
                SuspiciousPhoneTransferDto dto =
                        transferService.analyzePhoneTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getPhoneTransfersBlocked().increment();
                yield toPaymentResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
            default -> {
                if (accountRepo.findByAccountTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: account paymentId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildPaymentResponseFromAccount(request, internalId);
                }
                SuspiciousAccountTransferDto dto =
                        transferService.analyzeAccountTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getAccountTransfersBlocked().increment();
                yield toPaymentResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
        };
    }

    // ── private: анализ для transfer-service (TransferAntifraudRequestEvent) ─

    private com.bank.antifraud.dto.TransferAntifraudResponseEvent analyzeTransfer(
            com.bank.antifraud.dto.TransferAntifraudRequestEvent request) {

        Integer internalId = request.getTransferId().intValue();
        String type = request.getTransferType() == null ? "ACCOUNT" : request.getTransferType();

        return switch (type) {
            case "CARD" -> {
                if (cardRepo.findByCardTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: card transferId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildTransferResponseFromCard(request, internalId);
                }
                SuspiciousCardTransferDto dto =
                        transferService.analyzeCardTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getCardTransfersBlocked().increment();
                yield toTransferResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
            case "PHONE" -> {
                if (phoneRepo.findByPhoneTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: phone transferId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildTransferResponseFromPhone(request, internalId);
                }
                SuspiciousPhoneTransferDto dto =
                        transferService.analyzePhoneTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getPhoneTransfersBlocked().increment();
                yield toTransferResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
            default -> {
                if (accountRepo.findByAccountTransferId(internalId).isPresent()) {
                    log.warn("Idempotency: account transferId={} already analyzed", internalId);
                    metrics.getIdempotentSkipped().increment();
                    yield buildTransferResponseFromAccount(request, internalId);
                }
                SuspiciousAccountTransferDto dto =
                        transferService.analyzeAccountTransfer(request.getAmount(), internalId);
                if (dto.isBlocked()) metrics.getAccountTransfersBlocked().increment();
                yield toTransferResponse(request, dto.isBlocked(), dto.isSuspicious(),
                        dto.getBlockedReason(), internalId);
            }
        };
    }

    // ── helpers: payment response ─────────────────────────────────────────────

    private AntifraudResponseEvent toPaymentResponse(AntifraudRequestEvent req,
                                                      boolean blocked, boolean suspicious,
                                                      String reason, Integer transferId) {
        FraudDecision decision = resolveDecision(blocked, suspicious);
        return AntifraudResponseEvent.builder()
                .paymentId(req.getPaymentId())
                .transferType(req.getTransferType())
                .transferId(transferId)
                .decision(decision)
                .reason(reason)
                .riskScore(riskScore(decision))
                .build();
    }

    private AntifraudResponseEvent buildPaymentResponseFromCard(AntifraudRequestEvent req, Integer id) {
        return cardRepo.findByCardTransferId(id).map(e ->
                toPaymentResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    private AntifraudResponseEvent buildPaymentResponseFromPhone(AntifraudRequestEvent req, Integer id) {
        return phoneRepo.findByPhoneTransferId(id).map(e ->
                toPaymentResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    private AntifraudResponseEvent buildPaymentResponseFromAccount(AntifraudRequestEvent req, Integer id) {
        return accountRepo.findByAccountTransferId(id).map(e ->
                toPaymentResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    // ── helpers: transfer response ────────────────────────────────────────────

    private com.bank.antifraud.dto.TransferAntifraudResponseEvent toTransferResponse(
            com.bank.antifraud.dto.TransferAntifraudRequestEvent req,
            boolean blocked, boolean suspicious, String reason, Integer transferId) {
        FraudDecision decision = resolveDecision(blocked, suspicious);
        return com.bank.antifraud.dto.TransferAntifraudResponseEvent.builder()
                .transferId(req.getTransferId())
                .transferType(req.getTransferType())
                .decision(decision.name())
                .reason(reason)
                .riskScore(riskScore(decision))
                .build();
    }

    private com.bank.antifraud.dto.TransferAntifraudResponseEvent buildTransferResponseFromCard(
            com.bank.antifraud.dto.TransferAntifraudRequestEvent req, Integer id) {
        return cardRepo.findByCardTransferId(id).map(e ->
                toTransferResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    private com.bank.antifraud.dto.TransferAntifraudResponseEvent buildTransferResponseFromPhone(
            com.bank.antifraud.dto.TransferAntifraudRequestEvent req, Integer id) {
        return phoneRepo.findByPhoneTransferId(id).map(e ->
                toTransferResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    private com.bank.antifraud.dto.TransferAntifraudResponseEvent buildTransferResponseFromAccount(
            com.bank.antifraud.dto.TransferAntifraudRequestEvent req, Integer id) {
        return accountRepo.findByAccountTransferId(id).map(e ->
                toTransferResponse(req, e.isBlocked(), e.isSuspicious(), e.getBlockedReason(), id)
        ).orElseThrow();
    }

    // ── shared logic ──────────────────────────────────────────────────────────

    private FraudDecision resolveDecision(boolean blocked, boolean suspicious) {
        if (blocked)    return FraudDecision.BLOCK;
        if (suspicious) return FraudDecision.REVIEW;
        return FraudDecision.ALLOW;
    }

    private int riskScore(FraudDecision decision) {
        return switch (decision) {
            case ALLOW  -> 10;
            case REVIEW -> 55;
            case BLOCK  -> 90;
        };
    }
}
