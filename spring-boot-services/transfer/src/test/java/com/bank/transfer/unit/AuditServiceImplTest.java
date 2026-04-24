package com.bank.transfer.unit;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.AuditDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;
import com.bank.transfer.entity.Audit;
import com.bank.transfer.mapper.AuditMapper;
import com.bank.transfer.outbox.HistoryOutboxHelper;
import com.bank.transfer.repository.AuditRepository;
import com.bank.transfer.service.AuditServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты AuditServiceImpl (transfer-service).
 */
 /**
 * Реальные зависимости сервиса:
 *   - AuditRepository      — JPA
 *   - AuditMapper          — MapStruct
 *   - ObjectMapper         — Jackson
 *   - HistoryOutboxHelper  — Outbox-паттерн → history-service
 */
 /**
 * TransferProducer и KafkaErrorPublisher не являются зависимостями
 * AuditServiceImpl и были удалены при рефакторинге на Outbox-паттерн.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditRepository    auditRepository;
    @Mock private AuditMapper        auditMapper;
    @Mock private ObjectMapper       objectMapper;
    @Mock private HistoryOutboxHelper historyOutboxHelper;

    @InjectMocks
    private AuditServiceImpl auditService;

    private Audit         audit;
    private AuditDto      auditDto;
    private AccountTransferDto accountTransferDto;
    private CardTransferDto    cardTransferDto;
    private PhoneTransferDto   phoneTransferDto;

    @BeforeEach
    void setUp() {
        audit              = createAudit();
        auditDto           = createAuditDto();
        accountTransferDto = createAccountTransferDto();
        cardTransferDto    = createCardTransferDto();
        phoneTransferDto   = createPhoneTransferDto();
    }

    private Audit createAudit() {
        Audit a = new Audit();
        a.setId(TestConstants.ID);
        a.setOperationType(TestConstants.OPERATION_TYPE_CREATE);
        a.setEntityType(TestConstants.ENTITY_TYPE_PHONE_TRANSFER);
        a.setEntityJson("{\"phoneNumber\":" + TestConstants.PHONE_NUMBER + "}");
        a.setCreatedBy(TestConstants.SYSTEM_USER);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private AuditDto createAuditDto() {
        AuditDto d = new AuditDto();
        d.setId(TestConstants.ID);
        d.setOperationType(TestConstants.OPERATION_TYPE_CREATE);
        d.setEntityType(TestConstants.ENTITY_TYPE_PHONE_TRANSFER);
        d.setEntityJson("{\"phoneNumber\":" + TestConstants.PHONE_NUMBER + "}");
        d.setCreatedBy(TestConstants.SYSTEM_USER);
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    private AccountTransferDto createAccountTransferDto() {
        AccountTransferDto dto = new AccountTransferDto();
        dto.setId(TestConstants.ID);
        dto.setAccountNumber(TestConstants.ACCOUNT_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        return dto;
    }

    private CardTransferDto createCardTransferDto() {
        CardTransferDto dto = new CardTransferDto();
        dto.setId(TestConstants.ID);
        dto.setCardNumber(TestConstants.CARD_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        return dto;
    }

    private PhoneTransferDto createPhoneTransferDto() {
        PhoneTransferDto dto = new PhoneTransferDto();
        dto.setId(TestConstants.ID);
        dto.setPhoneNumber(TestConstants.PHONE_NUMBER);
        dto.setAmount(TestConstants.AMOUNT);
        dto.setAccountDetailsId(TestConstants.ACCOUNT_DETAILS_ID);
        return dto;
    }

    // ── getAuditHistory ───────────────────────────────────────────────────────

    @Test
    void getAuditHistory_success() {
        when(auditRepository.findAll()).thenReturn(List.of(audit));
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        List<AuditDto> result = auditService.getAuditHistory();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(auditDto.getOperationType(), result.get(0).getOperationType());
        assertEquals(auditDto.getEntityType(),    result.get(0).getEntityType());
        verify(auditRepository).findAll();
        verify(auditMapper).toDto(audit);
        verifyNoInteractions(historyOutboxHelper);
    }

    @Test
    void getAuditHistory_repositoryThrowsException_propagates() {
        when(auditRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> auditService.getAuditHistory());

        verify(auditRepository).findAll();
        verifyNoInteractions(auditMapper, historyOutboxHelper);
    }

    // ── auditAccountTransfer ──────────────────────────────────────────────────

    @Test
    void auditAccountTransfer_success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(accountTransferDto))
                .thenReturn("{\"accountNumber\":\"" + TestConstants.ACCOUNT_NUMBER + "\"}");
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        auditService.auditAccountTransfer(accountTransferDto);

        verify(objectMapper).writeValueAsString(accountTransferDto);
        verify(auditMapper).toEntity(any(AuditDto.class));
        verify(auditRepository).save(audit);
        verify(auditMapper).toDto(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
        verifyNoMoreInteractions(historyOutboxHelper);
    }

    @Test
    void auditAccountTransfer_jsonProcessingException_stillSaves() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(accountTransferDto))
                .thenThrow(new JsonProcessingException("Serialization error") {});
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        // JsonProcessingException → entityJson="{}" → audit всё равно сохраняется
        auditService.auditAccountTransfer(accountTransferDto);

        verify(auditRepository).save(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
    }

    // ── auditCardTransfer ─────────────────────────────────────────────────────

    @Test
    void auditCardTransfer_success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(cardTransferDto))
                .thenReturn("{\"cardNumber\":" + TestConstants.CARD_NUMBER + "}");
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        auditService.auditCardTransfer(cardTransferDto);

        verify(objectMapper).writeValueAsString(cardTransferDto);
        verify(auditRepository).save(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
        verifyNoMoreInteractions(historyOutboxHelper);
    }

    @Test
    void auditCardTransfer_jsonProcessingException_stillSaves() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(cardTransferDto))
                .thenThrow(new JsonProcessingException("Serialization error") {});
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        auditService.auditCardTransfer(cardTransferDto);

        verify(auditRepository).save(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
    }

    // ── auditPhoneTransfer ────────────────────────────────────────────────────

    @Test
    void auditPhoneTransfer_success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(phoneTransferDto))
                .thenReturn("{\"phoneNumber\":" + TestConstants.PHONE_NUMBER + "}");
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        auditService.auditPhoneTransfer(phoneTransferDto);

        verify(objectMapper).writeValueAsString(phoneTransferDto);
        verify(auditRepository).save(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
        verifyNoMoreInteractions(historyOutboxHelper);
    }

    @Test
    void auditPhoneTransfer_jsonProcessingException_stillSaves() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(phoneTransferDto))
                .thenThrow(new JsonProcessingException("Serialization error") {});
        when(auditMapper.toEntity(any(AuditDto.class))).thenReturn(audit);
        when(auditRepository.save(audit)).thenReturn(audit);
        when(auditMapper.toDto(audit)).thenReturn(auditDto);

        auditService.auditPhoneTransfer(phoneTransferDto);

        verify(auditRepository).save(audit);
        verify(historyOutboxHelper).enqueueTransferEvent(anyString(), anyString(), any(AuditDto.class));
    }
}
