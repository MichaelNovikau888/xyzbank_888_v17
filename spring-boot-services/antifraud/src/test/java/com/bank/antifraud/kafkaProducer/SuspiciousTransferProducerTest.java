package com.bank.antifraud.kafkaProducer;

import com.bank.antifraud.dto.AntifraudResponseEvent;
import com.bank.antifraud.dto.TransferAntifraudResponseEvent;
import com.bank.antifraud.enums.FraudDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SuspiciousTransferProducerTest {

    private static final String PAYMENT_ANTIFRAUD_RESPONSE = "payment.antifraud.response";
    private static final String TRANSFER_ANTIFRAUD_RESPONSE = "transfer.antifraud.response";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SuspiciousTransferProducer suspiciousTransferProducer;

    // ── sendAntifraudResponse (payment-api) ──────────────────────────────────

    @Test
    void sendAntifraudResponse_ShouldSendToPaymentResponseTopic() {
        AntifraudResponseEvent event = new AntifraudResponseEvent();
        event.setPaymentId(42L);
        event.setDecision(FraudDecision.ALLOW.name());

        suspiciousTransferProducer.sendAntifraudResponse(event);

        verify(kafkaTemplate).send(eq(PAYMENT_ANTIFRAUD_RESPONSE), eq("42"), eq(event));
    }

    @Test
    void sendAntifraudResponse_NullPaymentId_ShouldUseUnknownKey() {
        AntifraudResponseEvent event = new AntifraudResponseEvent();
        event.setPaymentId(null);
        event.setDecision(FraudDecision.BLOCK.name());

        suspiciousTransferProducer.sendAntifraudResponse(event);

        verify(kafkaTemplate).send(eq(PAYMENT_ANTIFRAUD_RESPONSE), eq("unknown"), any());
    }

    // ── sendTransferAntifraudResponse (transfer-service) ────────────────────

    @Test
    void sendTransferAntifraudResponse_ShouldSendToTransferResponseTopic() {
        TransferAntifraudResponseEvent event = new TransferAntifraudResponseEvent();
        event.setTransferId(99L);
        event.setDecision(FraudDecision.REVIEW.name());

        suspiciousTransferProducer.sendTransferAntifraudResponse(event);

        verify(kafkaTemplate).send(eq(TRANSFER_ANTIFRAUD_RESPONSE), eq("99"), eq(event));
    }

    @Test
    void sendTransferAntifraudResponse_NullTransferId_ShouldUseUnknownKey() {
        TransferAntifraudResponseEvent event = new TransferAntifraudResponseEvent();
        event.setTransferId(null);
        event.setDecision(FraudDecision.ALLOW.name());

        suspiciousTransferProducer.sendTransferAntifraudResponse(event);

        verify(kafkaTemplate).send(eq(TRANSFER_ANTIFRAUD_RESPONSE), eq("unknown"), any());
    }
}
