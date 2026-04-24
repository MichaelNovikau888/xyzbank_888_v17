package com.bank.transfer.unit;

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
import com.bank.transfer.outbox.OutboxRepository;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;
import com.bank.transfer.service.TransferServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты TransferServiceImpl.
 */
 /**
 * После рефакторинга каждый save* метод выполняет 3 outbox-операции:
 *   1. saveToOutbox("suspicious-transfers.create", ...) — OutboxRepository напрямую
 *   2. antifraudOutboxHelper.enqueue*TransferCheck(...)  — transfer.antifraud.check
 *   3. notificationOutboxHelper.enqueueCreated(...)       — transfer.notification (CREATED)
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock private AccountTransferRepository        accountTransferRepository;
    @Mock private CardTransferRepository           cardTransferRepository;
    @Mock private PhoneTransferRepository          phoneTransferRepository;
    @Mock private AccountTransferMapper            accountTransferMapper;
    @Mock private CardTransferMapper               cardTransferMapper;
    @Mock private PhoneTransferMapper              phoneTransferMapper;
    @Mock private KafkaErrorPublisher              errorPublisher;
    @Mock private OutboxRepository                 outboxRepository;
    @Mock private ObjectMapper                     objectMapper;
    @Mock private TransferMetrics                  metrics;
    @Mock private AntifraudOutboxHelper            antifraudOutboxHelper;
    @Mock private TransferNotificationOutboxHelper notificationOutboxHelper;

    @InjectMocks
    private TransferServiceImpl transferService;

    private AccountTransferDto accountTransferDto;
    private AccountTransfer    accountTransfer;
    private CardTransferDto    cardTransferDto;
    private CardTransfer       cardTransfer;
    private PhoneTransferDto   phoneTransferDto;
    private PhoneTransfer      phoneTransfer;

    @BeforeEach
    void setUp() throws Exception {
        Counter mockCounter = mock(Counter.class);
        DistributionSummary mockSummary = mock(DistributionSummary.class);
        when(metrics.getAccountTransferSaved()).thenReturn(mockCounter);
        when(metrics.getCardTransferSaved()).thenReturn(mockCounter);
        when(metrics.getPhoneTransferSaved()).thenReturn(mockCounter);
        when(metrics.getAccountTransferSkipped()).thenReturn(mockCounter);
        when(metrics.getCardTransferSkipped()).thenReturn(mockCounter);
        when(metrics.getPhoneTransferSkipped()).thenReturn(mockCounter);
        when(metrics.getTransferFailed()).thenReturn(mockCounter);
        when(metrics.getTransferAmountSummary()).thenReturn(mockSummary);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":1}");

        accountTransferDto = buildAccountTransferDto();
        accountTransfer    = buildAccountTransfer();
        cardTransferDto    = buildCardTransferDto();
        cardTransfer       = buildCardTransfer();
        phoneTransferDto   = buildPhoneTransferDto();
        phoneTransfer      = buildPhoneTransfer();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // saveAccountTransfer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveAccountTransfer: успех — все 3 outbox-вызова выполнены")
    void saveAccountTransfer_success_allThreeOutboxCalled() {
        when(accountTransferMapper.toEntity(accountTransferDto)).thenReturn(accountTransfer);
        when(accountTransferRepository.save(accountTransfer)).thenReturn(accountTransfer);
        when(accountTransferMapper.toDto(accountTransfer)).thenReturn(accountTransferDto);

        transferService.saveAccountTransfer(accountTransferDto);

        verify(outboxRepository).save(any());
        verify(antifraudOutboxHelper).enqueueAccountTransferCheck(
                eq(TestConstants.ID), eq(TestConstants.AMOUNT), any());
        verify(notificationOutboxHelper).enqueueCreated(
                eq(TestConstants.ID), eq(TestConstants.CLIENT_ID), eq("ACCOUNT"),
                eq(TestConstants.AMOUNT), eq("RUB"), eq(TestConstants.ACCOUNT_NUMBER), any());
        verifyNoInteractions(errorPublisher);
    }

    @Test
    @DisplayName("saveAccountTransfer: дубль — idempotency guard, outbox не вызывается")
    void saveAccountTransfer_idempotent_skipsAllOutbox() {
        when(accountTransferRepository.existsByAccountNumberAndAccountDetailsIdAndAmount(
                TestConstants.ACCOUNT_NUMBER, TestConstants.ACCOUNT_DETAILS_ID, TestConstants.AMOUNT))
                .thenReturn(true);

        transferService.saveAccountTransfer(accountTransferDto);

        verifyNoInteractions(accountTransferMapper, antifraudOutboxHelper, notificationOutboxHelper, errorPublisher);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveAccountTransfer: ошибка репозитория — errorPublisher вызван, outbox-хелперы нет")
    void saveAccountTransfer_repositoryFails_publishesError() {
        when(accountTransferMapper.toEntity(accountTransferDto)).thenReturn(accountTransfer);
        when(accountTransferRepository.save(accountTransfer)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> transferService.saveAccountTransfer(accountTransferDto));

        verify(errorPublisher).publish(any(Exception.class), eq(null));
        verifyNoInteractions(antifraudOutboxHelper, notificationOutboxHelper);
    }

    @Test
    @DisplayName("saveAccountTransfer: null DTO — IllegalArgumentException, mapper не вызывается")
    void saveAccountTransfer_nullDto_throwsAndPublishesError() {
        assertThrows(RuntimeException.class, () -> transferService.saveAccountTransfer(null));

        verify(errorPublisher).publish(any(IllegalArgumentException.class), eq(null));
        verifyNoInteractions(accountTransferMapper, accountTransferRepository,
                antifraudOutboxHelper, notificationOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // saveCardTransfer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveCardTransfer: успех — 3 outbox-вызова, номер карты маскируется")
    void saveCardTransfer_success_allThreeOutboxCalled() {
        when(cardTransferMapper.toEntity(cardTransferDto)).thenReturn(cardTransfer);
        when(cardTransferRepository.save(cardTransfer)).thenReturn(cardTransfer);
        when(cardTransferMapper.toDto(cardTransfer)).thenReturn(cardTransferDto);

        transferService.saveCardTransfer(cardTransferDto);

        verify(outboxRepository).save(any());
        verify(antifraudOutboxHelper).enqueueCardTransferCheck(
                eq(TestConstants.ID), eq(TestConstants.AMOUNT), any());
        // recipientDisplay = masked card — проверяем только clientId и тип
        verify(notificationOutboxHelper).enqueueCreated(
                eq(TestConstants.ID), eq(TestConstants.CLIENT_ID), eq("CARD"),
                eq(TestConstants.AMOUNT), eq("RUB"), anyString(), any());
        verifyNoInteractions(errorPublisher);
    }

    @Test
    @DisplayName("saveCardTransfer: дубль — пропускается")
    void saveCardTransfer_idempotent_skipsAllOutbox() {
        when(cardTransferRepository.existsByCardNumberAndAccountDetailsIdAndAmount(
                TestConstants.CARD_NUMBER, TestConstants.ACCOUNT_DETAILS_ID, TestConstants.AMOUNT))
                .thenReturn(true);

        transferService.saveCardTransfer(cardTransferDto);

        verifyNoInteractions(cardTransferMapper, antifraudOutboxHelper, notificationOutboxHelper, errorPublisher);
    }

    @Test
    @DisplayName("saveCardTransfer: ошибка репозитория — errorPublisher вызван")
    void saveCardTransfer_repositoryFails_publishesError() {
        when(cardTransferMapper.toEntity(cardTransferDto)).thenReturn(cardTransfer);
        when(cardTransferRepository.save(cardTransfer)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> transferService.saveCardTransfer(cardTransferDto));

        verify(errorPublisher).publish(any(Exception.class), eq(null));
        verifyNoInteractions(antifraudOutboxHelper, notificationOutboxHelper);
    }

    @Test
    @DisplayName("saveCardTransfer: null DTO — errorPublisher, mapper не вызывается")
    void saveCardTransfer_nullDto_throwsAndPublishesError() {
        assertThrows(RuntimeException.class, () -> transferService.saveCardTransfer(null));

        verify(errorPublisher).publish(any(IllegalArgumentException.class), eq(null));
        verifyNoInteractions(cardTransferMapper, cardTransferRepository,
                antifraudOutboxHelper, notificationOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // savePhoneTransfer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("savePhoneTransfer: успех — 3 outbox-вызова, телефон маскируется")
    void savePhoneTransfer_success_allThreeOutboxCalled() {
        when(phoneTransferMapper.toEntity(phoneTransferDto)).thenReturn(phoneTransfer);
        when(phoneTransferRepository.save(phoneTransfer)).thenReturn(phoneTransfer);
        when(phoneTransferMapper.toDto(phoneTransfer)).thenReturn(phoneTransferDto);

        transferService.savePhoneTransfer(phoneTransferDto);

        verify(outboxRepository).save(any());
        verify(antifraudOutboxHelper).enqueuePhoneTransferCheck(
                eq(TestConstants.ID), eq(TestConstants.AMOUNT), any());
        verify(notificationOutboxHelper).enqueueCreated(
                eq(TestConstants.ID), eq(TestConstants.CLIENT_ID), eq("PHONE"),
                eq(TestConstants.AMOUNT), eq("RUB"), anyString(), any());
        verifyNoInteractions(errorPublisher);
    }

    @Test
    @DisplayName("savePhoneTransfer: дубль — пропускается")
    void savePhoneTransfer_idempotent_skipsAllOutbox() {
        when(phoneTransferRepository.existsByPhoneNumberAndAccountDetailsIdAndAmount(
                TestConstants.PHONE_NUMBER, TestConstants.ACCOUNT_DETAILS_ID, TestConstants.AMOUNT))
                .thenReturn(true);

        transferService.savePhoneTransfer(phoneTransferDto);

        verifyNoInteractions(phoneTransferMapper, antifraudOutboxHelper, notificationOutboxHelper, errorPublisher);
    }

    @Test
    @DisplayName("savePhoneTransfer: ошибка репозитория — errorPublisher вызван")
    void savePhoneTransfer_repositoryFails_publishesError() {
        when(phoneTransferMapper.toEntity(phoneTransferDto)).thenReturn(phoneTransfer);
        when(phoneTransferRepository.save(phoneTransfer)).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> transferService.savePhoneTransfer(phoneTransferDto));

        verify(errorPublisher).publish(any(Exception.class), eq(null));
        verifyNoInteractions(antifraudOutboxHelper, notificationOutboxHelper);
    }

    @Test
    @DisplayName("savePhoneTransfer: null DTO — errorPublisher, mapper не вызывается")
    void savePhoneTransfer_nullDto_throwsAndPublishesError() {
        assertThrows(RuntimeException.class, () -> transferService.savePhoneTransfer(null));

        verify(errorPublisher).publish(any(IllegalArgumentException.class), eq(null));
        verifyNoInteractions(phoneTransferMapper, phoneTransferRepository,
                antifraudOutboxHelper, notificationOutboxHelper);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Builders
    // ══════════════════════════════════════════════════════════════════════════

    private AccountTransferDto buildAccountTransferDto() {
        AccountTransferDto dto = new AccountTransferDto();
        dto.setAccountNumber(TestConstants.ACCOUNT_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        dto.setClientId(TestConstants.CLIENT_ID);
        dto.setPurpose(TestConstants.PURPOSE);
        return dto;
    }

    private AccountTransfer buildAccountTransfer() {
        AccountTransfer e = new AccountTransfer();
        e.setId(TestConstants.ID);
        e.setAccountNumber(TestConstants.ACCOUNT_NUMBER);
        e.setAmount(TestConstants.AMOUNT);
        e.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        e.setClientId(TestConstants.CLIENT_ID);
        e.setPurpose(TestConstants.PURPOSE);
        e.setStatus(TransferStatus.CREATED);
        return e;
    }

    private CardTransferDto buildCardTransferDto() {
        CardTransferDto dto = new CardTransferDto();
        dto.setCardNumber(TestConstants.CARD_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        dto.setClientId(TestConstants.CLIENT_ID);
        dto.setPurpose(TestConstants.PURPOSE);
        return dto;
    }

    private CardTransfer buildCardTransfer() {
        CardTransfer e = new CardTransfer();
        e.setId(TestConstants.ID);
        e.setCardNumber(TestConstants.CARD_NUMBER);
        e.setAmount(TestConstants.AMOUNT);
        e.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        e.setClientId(TestConstants.CLIENT_ID);
        e.setPurpose(TestConstants.PURPOSE);
        e.setStatus(TransferStatus.CREATED);
        return e;
    }

    private PhoneTransferDto buildPhoneTransferDto() {
        PhoneTransferDto dto = new PhoneTransferDto();
        dto.setPhoneNumber(TestConstants.PHONE_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        dto.setClientId(TestConstants.CLIENT_ID);
        dto.setPurpose(TestConstants.PURPOSE);
        return dto;
    }

    private PhoneTransfer buildPhoneTransfer() {
        PhoneTransfer e = new PhoneTransfer();
        e.setId(TestConstants.ID);
        e.setPhoneNumber(TestConstants.PHONE_NUMBER);
        e.setAmount(TestConstants.AMOUNT);
        e.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        e.setClientId(TestConstants.CLIENT_ID);
        e.setPurpose(TestConstants.PURPOSE);
        e.setStatus(TransferStatus.CREATED);
        return e;
    }
}
