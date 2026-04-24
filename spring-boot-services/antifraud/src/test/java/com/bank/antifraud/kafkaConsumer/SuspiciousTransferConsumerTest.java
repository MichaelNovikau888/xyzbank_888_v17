package com.bank.antifraud.kafkaConsumer;

import com.bank.antifraud.dto.AntifraudRequestEvent;
import com.bank.antifraud.dto.AntifraudResponseEvent;
import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.dto.TransferAntifraudRequestEvent;
import com.bank.antifraud.dto.TransferAntifraudResponseEvent;
import com.bank.antifraud.enums.FraudDecision;
import com.bank.antifraud.kafkaProducer.SuspiciousTransferProducer;
import com.bank.antifraud.metrics.AntifraudMetrics;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import com.bank.antifraud.service.SuspiciousTransferServiceImpl;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты SuspiciousTransferConsumer.
 */
 /**
 * Тестируем оба handler-метода напрямую — Kafka-инфраструктура не нужна.
 * Используем реальные DTO (AntifraudRequestEvent, TransferAntifraudRequestEvent).
 */
@ExtendWith(MockitoExtension.class)
class SuspiciousTransferConsumerTest {

    @Mock private SuspiciousTransferServiceImpl       transferService;
    @Mock private SuspiciousTransferProducer          kafkaProducer;
    @Mock private SuspiciousCardTransferRepository    cardRepo;
    @Mock private SuspiciousPhoneTransferRepository   phoneRepo;
    @Mock private SuspiciousAccountTransferRepository accountRepo;
    @Mock private AntifraudMetrics                    metrics;

    @InjectMocks
    private SuspiciousTransferConsumer consumer;

    private static final long TRANSFER_ID = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = org.mockito.Mockito.mock(Counter.class);
        when(metrics.getIdempotentSkipped()).thenReturn(mockCounter);
        when(metrics.getCardTransfersBlocked()).thenReturn(mockCounter);
        when(metrics.getPhoneTransfersBlocked()).thenReturn(mockCounter);
        when(metrics.getAccountTransfersBlocked()).thenReturn(mockCounter);
    }

    // ── handlePaymentAntifraudCheck ───────────────────────────────────────────

    @Test
    void handlePayment_cardTransfer_allowDecision() {
        AntifraudRequestEvent request = AntifraudRequestEvent.builder()
                .paymentId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("CARD")
                .build();

        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setSuspicious(false);
        dto.setBlocked(false);
        dto.setCardTransferId(TRANSFER_ID.intValue());

        when(cardRepo.findByCardTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzeCardTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handlePaymentAntifraudCheck(request);

        ArgumentCaptor<AntifraudResponseEvent> captor = ArgumentCaptor.forClass(AntifraudResponseEvent.class);
        verify(kafkaProducer).sendAntifraudResponse(captor.capture());
        assertEquals(FraudDecision.ALLOW, captor.getValue().getDecision());
        assertEquals(TRANSFER_ID, captor.getValue().getPaymentId());
    }

    @Test
    void handlePayment_cardTransfer_blockedDecision() {
        AntifraudRequestEvent request = AntifraudRequestEvent.builder()
                .paymentId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("CARD")
                .build();

        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setBlocked(true);
        dto.setSuspicious(true);
        dto.setBlockedReason("High risk");
        dto.setCardTransferId(TRANSFER_ID.intValue());

        when(cardRepo.findByCardTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzeCardTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handlePaymentAntifraudCheck(request);

        ArgumentCaptor<AntifraudResponseEvent> captor = ArgumentCaptor.forClass(AntifraudResponseEvent.class);
        verify(kafkaProducer).sendAntifraudResponse(captor.capture());
        assertEquals(FraudDecision.BLOCK, captor.getValue().getDecision());
        verify(metrics.getCardTransfersBlocked()).increment();
    }

    @Test
    void handlePayment_phoneTransfer_suspicious_reviewDecision() {
        AntifraudRequestEvent request = AntifraudRequestEvent.builder()
                .paymentId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("PHONE")
                .build();

        SuspiciousPhoneTransferDto dto = new SuspiciousPhoneTransferDto();
        dto.setSuspicious(true);
        dto.setBlocked(false);
        dto.setPhoneTransferId(TRANSFER_ID.intValue());

        when(phoneRepo.findByPhoneTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzePhoneTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handlePaymentAntifraudCheck(request);

        ArgumentCaptor<AntifraudResponseEvent> captor = ArgumentCaptor.forClass(AntifraudResponseEvent.class);
        verify(kafkaProducer).sendAntifraudResponse(captor.capture());
        assertEquals(FraudDecision.REVIEW, captor.getValue().getDecision());
    }

    @Test
    void handlePayment_accountTransfer_defaultType_allowDecision() {
        // null transferType → default ACCOUNT branch
        AntifraudRequestEvent request = AntifraudRequestEvent.builder()
                .paymentId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType(null)
                .build();

        SuspiciousAccountTransferDto dto = new SuspiciousAccountTransferDto();
        dto.setSuspicious(false);
        dto.setBlocked(false);
        dto.setAccountTransferId(TRANSFER_ID.intValue());

        when(accountRepo.findByAccountTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzeAccountTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handlePaymentAntifraudCheck(request);

        verify(kafkaProducer).sendAntifraudResponse(any(AntifraudResponseEvent.class));
        verify(transferService, never()).analyzeCardTransfer(any(), anyInt());
        verify(transferService, never()).analyzePhoneTransfer(any(), anyInt());
    }

    @Test
    void handlePayment_idempotency_cardAlreadyAnalyzed_skipsService() {
        AntifraudRequestEvent request = AntifraudRequestEvent.builder()
                .paymentId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("CARD")
                .build();

        com.bank.antifraud.model.SuspiciousCardTransfer existing =
                new com.bank.antifraud.model.SuspiciousCardTransfer();
        existing.setCardTransferId(TRANSFER_ID.intValue());
        existing.setBlocked(false);
        existing.setSuspicious(false);

        // First call: idempotency check returns present
        when(cardRepo.findByCardTransferId(TRANSFER_ID.intValue()))
                .thenReturn(Optional.of(existing));

        consumer.handlePaymentAntifraudCheck(request);

        verify(transferService, never()).analyzeCardTransfer(any(), anyInt());
        verify(metrics.getIdempotentSkipped()).increment();
        verify(kafkaProducer).sendAntifraudResponse(any(AntifraudResponseEvent.class));
    }

    // ── handleTransferAntifraudCheck ──────────────────────────────────────────

    @Test
    void handleTransfer_accountTransfer_allowDecision() {
        TransferAntifraudRequestEvent request = TransferAntifraudRequestEvent.builder()
                .transferId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("ACCOUNT")
                .build();

        SuspiciousAccountTransferDto dto = new SuspiciousAccountTransferDto();
        dto.setSuspicious(false);
        dto.setBlocked(false);
        dto.setAccountTransferId(TRANSFER_ID.intValue());

        when(accountRepo.findByAccountTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzeAccountTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handleTransferAntifraudCheck(request);

        ArgumentCaptor<TransferAntifraudResponseEvent> captor =
                ArgumentCaptor.forClass(TransferAntifraudResponseEvent.class);
        verify(kafkaProducer).sendTransferAntifraudResponse(captor.capture());
        assertEquals("ALLOW", captor.getValue().getDecision());
        assertEquals(TRANSFER_ID, captor.getValue().getTransferId());
    }

    @Test
    void handleTransfer_cardTransfer_blockedDecision() {
        TransferAntifraudRequestEvent request = TransferAntifraudRequestEvent.builder()
                .transferId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("CARD")
                .build();

        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setBlocked(true);
        dto.setSuspicious(true);
        dto.setBlockedReason("Stolen card");
        dto.setCardTransferId(TRANSFER_ID.intValue());

        when(cardRepo.findByCardTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzeCardTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handleTransferAntifraudCheck(request);

        ArgumentCaptor<TransferAntifraudResponseEvent> captor =
                ArgumentCaptor.forClass(TransferAntifraudResponseEvent.class);
        verify(kafkaProducer).sendTransferAntifraudResponse(captor.capture());
        assertEquals("BLOCK", captor.getValue().getDecision());
    }

    @Test
    void handleTransfer_phoneTransfer_reviewDecision() {
        TransferAntifraudRequestEvent request = TransferAntifraudRequestEvent.builder()
                .transferId(TRANSFER_ID)
                .amount(AMOUNT)
                .transferType("PHONE")
                .build();

        SuspiciousPhoneTransferDto dto = new SuspiciousPhoneTransferDto();
        dto.setSuspicious(true);
        dto.setBlocked(false);
        dto.setPhoneTransferId(TRANSFER_ID.intValue());

        when(phoneRepo.findByPhoneTransferId(TRANSFER_ID.intValue())).thenReturn(Optional.empty());
        when(transferService.analyzePhoneTransfer(AMOUNT, TRANSFER_ID.intValue())).thenReturn(dto);

        consumer.handleTransferAntifraudCheck(request);

        ArgumentCaptor<TransferAntifraudResponseEvent> captor =
                ArgumentCaptor.forClass(TransferAntifraudResponseEvent.class);
        verify(kafkaProducer).sendTransferAntifraudResponse(captor.capture());
        assertEquals("REVIEW", captor.getValue().getDecision());
    }
}
