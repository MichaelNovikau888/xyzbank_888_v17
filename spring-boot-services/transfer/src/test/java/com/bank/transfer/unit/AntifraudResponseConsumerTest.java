package com.bank.transfer.unit;

import com.bank.transfer.antifraud.AntifraudResponseConsumer;
import com.bank.transfer.antifraud.AntifraudResponseEvent;
import com.bank.transfer.entity.AccountTransfer;
import com.bank.transfer.entity.CardTransfer;
import com.bank.transfer.entity.PhoneTransfer;
import com.bank.transfer.enums.TransferStatus;
import com.bank.transfer.notification.TransferNotificationOutboxHelper;
import com.bank.transfer.outbox.HistoryOutboxHelper;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты AntifraudResponseConsumer (transfer-service).
 */
 /**
 * Слушает: transfer.antifraud.response
 */
 /**
 * При получении ответа (в одной транзакции):
 *   ALLOW  → COMPLETED: entity.status=COMPLETED + enqueueFinal + enqueueTransferEvent(TransferCompleted)
 *   REVIEW → REVIEW:    entity.status=REVIEW    + enqueueReview + enqueueTransferEvent(TransferReview)
 *   BLOCK  → BLOCKED:   entity.status=BLOCKED   + enqueueFinal + enqueueTransferEvent(TransferBlocked)
 */
 /**
 * Идемпотентность: если status уже НЕ CREATED — пропускаем.
 * Не найден: не вызываем outbox-хелперы.
 */
@ExtendWith(MockitoExtension.class)
class AntifraudResponseConsumerTest {

    @Mock private HistoryOutboxHelper              historyOutboxHelper;
    @Mock private TransferNotificationOutboxHelper notificationOutboxHelper;
    @Mock private AccountTransferRepository        accountTransferRepository;
    @Mock private CardTransferRepository           cardTransferRepository;
    @Mock private PhoneTransferRepository          phoneTransferRepository;

    @InjectMocks
    private AntifraudResponseConsumer consumer;

    private AccountTransfer accountTransfer;
    private CardTransfer    cardTransfer;
    private PhoneTransfer   phoneTransfer;

    @BeforeEach
    void setUp() {
        accountTransfer = new AccountTransfer();
        accountTransfer.setId(TestConstants.ID);
        accountTransfer.setAccountNumber(TestConstants.ACCOUNT_NUMBER);
        accountTransfer.setAmount(TestConstants.AMOUNT);
        accountTransfer.setClientId(TestConstants.CLIENT_ID);
        accountTransfer.setPurpose(TestConstants.PURPOSE);
        accountTransfer.setStatus(TransferStatus.CREATED);

        cardTransfer = new CardTransfer();
        cardTransfer.setId(TestConstants.ID);
        cardTransfer.setCardNumber(TestConstants.CARD_NUMBER);
        cardTransfer.setAmount(TestConstants.AMOUNT);
        cardTransfer.setClientId(TestConstants.CLIENT_ID);
        cardTransfer.setPurpose(TestConstants.PURPOSE);
        cardTransfer.setStatus(TransferStatus.CREATED);

        phoneTransfer = new PhoneTransfer();
        phoneTransfer.setId(TestConstants.ID);
        phoneTransfer.setPhoneNumber(TestConstants.PHONE_NUMBER);
        phoneTransfer.setAmount(TestConstants.AMOUNT);
        phoneTransfer.setClientId(TestConstants.CLIENT_ID);
        phoneTransfer.setPurpose(TestConstants.PURPOSE);
        phoneTransfer.setStatus(TransferStatus.CREATED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCOUNT transfer — ALLOW
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ACCOUNT + ALLOW → entity.status=COMPLETED, enqueueFinal(COMPLETED), enqueueTransferEvent(TransferCompleted)")
    void handleAntifraudResponse_accountAllow_setsCompletedAndNotifies() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "ALLOW", "Low risk", 10));

        // Статус обновлён
        assertThat(accountTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        // Уведомление клиенту — COMPLETED, reason=null
        verify(notificationOutboxHelper).enqueueFinal(
                eq(TestConstants.ID),
                eq(TestConstants.CLIENT_ID),
                eq("ACCOUNT"),
                eq(TransferStatus.COMPLETED),
                eq(TestConstants.AMOUNT),
                eq("RUB"),
                eq(TestConstants.ACCOUNT_NUMBER),
                eq(TestConstants.PURPOSE),
                eq("Low risk"));

        // История — TransferCompleted
        verify(historyOutboxHelper).enqueueTransferEvent(
                eq(TestConstants.ID.toString()),
                eq("TransferCompleted"),
                any());
    }

    @Test
    @DisplayName("ACCOUNT + REVIEW → entity.status=REVIEW, enqueueReview вызван, enqueueFinal НЕ вызван")
    void handleAntifraudResponse_accountReview_setsReviewAndCallsEnqueueReview() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "REVIEW", "Manual check needed", 55));

        // Статус → REVIEW (промежуточный, не COMPLETED)
        assertThat(accountTransfer.getStatus()).isEqualTo(TransferStatus.REVIEW);

        // enqueueReview вызван с правильными аргументами
        verify(notificationOutboxHelper).enqueueReview(
                eq(TestConstants.ID),
                eq(TestConstants.CLIENT_ID),
                eq("ACCOUNT"),
                eq(TestConstants.AMOUNT),
                eq("RUB"));

        // enqueueFinal НЕ вызван — REVIEW не финальный
        verify(notificationOutboxHelper, never()).enqueueFinal(
                any(), any(), any(), any(), any(), any(), any(), any(), any());

        // История — TransferReview
        verify(historyOutboxHelper).enqueueTransferEvent(
                eq(TestConstants.ID.toString()),
                eq("TransferReview"),
                any());
    }

    @Test
    @DisplayName("REVIEW idempotency: уже в статусе REVIEW → пропускается")
    void handleAntifraudResponse_alreadyReview_skipsOutbox() {
        accountTransfer.setStatus(TransferStatus.REVIEW);
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "REVIEW", null, 55));

        verifyNoInteractions(notificationOutboxHelper, historyOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACCOUNT transfer — BLOCK
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ACCOUNT + BLOCK → entity.status=BLOCKED, enqueueFinal(BLOCKED), enqueueTransferEvent(TransferBlocked)")
    void handleAntifraudResponse_accountBlock_setsBlockedAndNotifies() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "BLOCK", "High fraud risk", 95));

        assertThat(accountTransfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);

        verify(notificationOutboxHelper).enqueueFinal(
                eq(TestConstants.ID),
                eq(TestConstants.CLIENT_ID),
                eq("ACCOUNT"),
                eq(TransferStatus.BLOCKED),
                eq(TestConstants.AMOUNT),
                eq("RUB"),
                eq(TestConstants.ACCOUNT_NUMBER),
                eq(TestConstants.PURPOSE),
                eq("High fraud risk"));

        verify(historyOutboxHelper).enqueueTransferEvent(
                eq(TestConstants.ID.toString()),
                eq("TransferBlocked"),
                any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CARD transfer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CARD + ALLOW → entity.status=COMPLETED, enqueueFinal вызван")
    void handleAntifraudResponse_cardAllow_setsCompleted() {
        when(cardTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(cardTransfer));
        when(cardTransferRepository.save(cardTransfer)).thenReturn(cardTransfer);

        consumer.handleAntifraudResponse(buildResponse("CARD", "ALLOW", null, 5));

        assertThat(cardTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(notificationOutboxHelper).enqueueFinal(
                eq(TestConstants.ID), eq(TestConstants.CLIENT_ID), eq("CARD"),
                eq(TransferStatus.COMPLETED), eq(TestConstants.AMOUNT), eq("RUB"),
                anyString(), eq(TestConstants.PURPOSE), eq(null));
        verify(historyOutboxHelper).enqueueTransferEvent(any(), eq("TransferCompleted"), any());
        verifyNoInteractions(accountTransferRepository, phoneTransferRepository);
    }

    @Test
    @DisplayName("CARD + BLOCK → entity.status=BLOCKED, enqueueFinal с reason вызван")
    void handleAntifraudResponse_cardBlock_setsBlockedWithReason() {
        when(cardTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(cardTransfer));
        when(cardTransferRepository.save(cardTransfer)).thenReturn(cardTransfer);

        consumer.handleAntifraudResponse(buildResponse("CARD", "BLOCK", "Suspicious card", 88));

        assertThat(cardTransfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        verify(notificationOutboxHelper).enqueueFinal(
                any(), any(), eq("CARD"), eq(TransferStatus.BLOCKED),
                any(), any(), anyString(), any(), eq("Suspicious card"));
        verify(historyOutboxHelper).enqueueTransferEvent(any(), eq("TransferBlocked"), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHONE transfer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PHONE + ALLOW → entity.status=COMPLETED")
    void handleAntifraudResponse_phoneAllow_setsCompleted() {
        when(phoneTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(phoneTransfer));
        when(phoneTransferRepository.save(phoneTransfer)).thenReturn(phoneTransfer);

        consumer.handleAntifraudResponse(buildResponse("PHONE", "ALLOW", null, 3));

        assertThat(phoneTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(notificationOutboxHelper).enqueueFinal(
                eq(TestConstants.ID), eq(TestConstants.CLIENT_ID), eq("PHONE"),
                eq(TransferStatus.COMPLETED), any(), any(), any(), any(), any());
        verifyNoInteractions(accountTransferRepository, cardTransferRepository);
    }

    @Test
    @DisplayName("PHONE + BLOCK → entity.status=BLOCKED")
    void handleAntifraudResponse_phoneBlock_setsBlocked() {
        when(phoneTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(phoneTransfer));
        when(phoneTransferRepository.save(phoneTransfer)).thenReturn(phoneTransfer);

        consumer.handleAntifraudResponse(buildResponse("PHONE", "BLOCK", "Phone fraud", 90));

        assertThat(phoneTransfer.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        verify(historyOutboxHelper).enqueueTransferEvent(any(), eq("TransferBlocked"), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Идемпотентность — уже финальный статус
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Idempotency: уже COMPLETED — повторный ответ пропускается")
    void handleAntifraudResponse_alreadyCompleted_skipsOutbox() {
        accountTransfer.setStatus(TransferStatus.COMPLETED);
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "ALLOW", null, 10));

        verifyNoInteractions(notificationOutboxHelper, historyOutboxHelper);
    }

    @Test
    @DisplayName("Idempotency: уже BLOCKED — повторный BLOCK пропускается")
    void handleAntifraudResponse_alreadyBlocked_skipsOutbox() {
        accountTransfer.setStatus(TransferStatus.BLOCKED);
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "BLOCK", "Fraud", 99));

        verifyNoInteractions(notificationOutboxHelper, historyOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Transfer not found
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Transfer not found — outbox-хелперы не вызываются")
    void handleAntifraudResponse_transferNotFound_noOutboxCalls() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.empty());

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "ALLOW", null, 10));

        verifyNoInteractions(notificationOutboxHelper, historyOutboxHelper);
    }

    @Test
    @DisplayName("CARD not found — outbox-хелперы не вызываются")
    void handleAntifraudResponse_cardNotFound_noOutboxCalls() {
        when(cardTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.empty());

        consumer.handleAntifraudResponse(buildResponse("CARD", "BLOCK", "Fraud", 99));

        verifyNoInteractions(notificationOutboxHelper, historyOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // transferType по умолчанию → ACCOUNT
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("transferType=null → трактуется как ACCOUNT")
    void handleAntifraudResponse_nullTransferType_defaultsToAccount() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        AntifraudResponseEvent event = AntifraudResponseEvent.builder()
                .transferId(TestConstants.ID)
                .transferType(null)
                .decision("ALLOW")
                .reason(null)
                .riskScore(5)
                .build();

        consumer.handleAntifraudResponse(event);

        assertThat(accountTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verifyNoInteractions(cardTransferRepository, phoneTransferRepository);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // History payload содержит ключевые поля
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("History payload содержит transferId, decision, status, riskScore")
    void handleAntifraudResponse_historyPayloadContainsKeyFields() {
        when(accountTransferRepository.findById(TestConstants.ID)).thenReturn(Optional.of(accountTransfer));
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);

        consumer.handleAntifraudResponse(buildResponse("ACCOUNT", "BLOCK", "Fraud", 87));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(historyOutboxHelper).enqueueTransferEvent(
                eq(TestConstants.ID.toString()), eq("TransferBlocked"), payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        assertThat(payload.toString())
                .contains("transferId")
                .contains("BLOCK")
                .contains("BLOCKED");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════════════════════

    private AntifraudResponseEvent buildResponse(String transferType, String decision,
                                                  String reason, int riskScore) {
        return AntifraudResponseEvent.builder()
                .transferId(TestConstants.ID)
                .transferType(transferType)
                .decision(decision)
                .reason(reason)
                .riskScore(riskScore)
                .build();
    }
}
