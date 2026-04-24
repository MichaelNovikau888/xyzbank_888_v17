package com.bank.antifraud.service;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.mappers.SuspiciousTransferMapper;
import com.bank.antifraud.model.SuspiciousCardTransfer;
import com.bank.antifraud.model.SuspiciousPhoneTransfer;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspiciousTransferServiceImplTest {

    @Mock
    private SuspiciousAccountTransferRepository accountTransferRepository;
    @Mock
    private SuspiciousCardTransferRepository cardTransferRepository;
    @Mock
    private SuspiciousPhoneTransferRepository phoneTransferRepository;
    @Mock
    private SuspiciousTransferMapper mapper;

    @InjectMocks
    private SuspiciousTransferServiceImpl service;

    private final BigDecimal suspiciousAmount = new BigDecimal("15000.00");
    private final BigDecimal normalAmount = new BigDecimal("5000.00");
    private final Integer transferId = 1;

    @Test
    void analyzeCardTransferWhenSuspiciousAmountShouldReturnBlockedTransfer() {

        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setSuspicious(true);
        dto.setBlocked(true);
        dto.setSuspiciousReason("Very big amount");
        dto.setCardTransferId(transferId);
        dto.setBlockedReason("Very big amount");

        when(mapper.toCardEntity(any())).thenReturn(new SuspiciousCardTransfer());

        SuspiciousCardTransferDto result = service.analyzeCardTransfer(suspiciousAmount, transferId);

        assertTrue(result.isSuspicious());
        assertTrue(result.isBlocked());
        assertEquals("Very big amount", result.getSuspiciousReason());
        assertEquals(transferId.longValue(), result.getCardTransferId());
        verify(cardTransferRepository).save(any());
    }

    @Test
    void analyzeCardTransferWhenNormalAmountShouldReturnCleanTransfer() {

        SuspiciousCardTransferDto result = service.analyzeCardTransfer(normalAmount, transferId);

        assertFalse(result.isSuspicious());
        assertFalse(result.isBlocked());
        assertEquals("Norm", result.getSuspiciousReason());
        assertEquals(transferId.longValue(), result.getCardTransferId());
        verify(cardTransferRepository).save(any());
    }

    @Test
    void analyzePhoneTransferWhenSuspiciousAmountShouldReturnBlockedTransfer() {

        when(mapper.toPhoneEntity(any())).thenReturn(new SuspiciousPhoneTransfer());

        SuspiciousPhoneTransferDto result = service.analyzePhoneTransfer(suspiciousAmount, transferId);

        assertTrue(result.isSuspicious());
        assertTrue(result.isBlocked());
        assertEquals("Very big amount", result.getSuspiciousReason());
        assertEquals(transferId.longValue(), result.getPhoneTransferId());
        verify(phoneTransferRepository).save(any());
    }

    @Test
    void analyzeAccountTransferWhenNormalAmountShouldReturnCleanTransfer() {

        SuspiciousAccountTransferDto result = service.analyzeAccountTransfer(normalAmount, transferId);

        assertFalse(result.isSuspicious());
        assertFalse(result.isBlocked());
        assertEquals("norm", result.getSuspiciousReason());
        assertEquals(transferId.longValue(), result.getAccountTransferId());
        verify(accountTransferRepository).save(any());
    }

    @Test
    void getCardTransferWhenExistsShouldReturnDto() {

        SuspiciousCardTransfer entity = new SuspiciousCardTransfer();
        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();

        when(cardTransferRepository.findById(transferId.longValue())).thenReturn(Optional.of(entity));
        when(mapper.toCardDTO(entity)).thenReturn(dto);

        SuspiciousCardTransferDto result = service.getCardTransfer(transferId);

        assertNotNull(result);
        assertEquals(dto, result);
    }

    @Test
    void getCardTransferWhenNotExistsShouldThrowException() {

        when(cardTransferRepository.findById(transferId.longValue())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getCardTransfer(transferId));
    }

    @Test
    void deleteAccountSuspiciousTransferShouldCallRepository() {

        service.deleteAccountSuspiciousTransfer(transferId);
        verify(accountTransferRepository).deleteById(transferId.longValue());
    }

    @Test
    void getPhoneTransferWhenExistsShouldReturnDto() {

        SuspiciousPhoneTransfer entity = new SuspiciousPhoneTransfer();
        SuspiciousPhoneTransferDto dto = new SuspiciousPhoneTransferDto();

        when(phoneTransferRepository.findById(transferId.longValue())).thenReturn(Optional.of(entity));
        when(mapper.toPhoneDTO(entity)).thenReturn(dto);

        SuspiciousPhoneTransferDto result = service.getPhoneTransfer(transferId);

        assertNotNull(result);
        assertEquals(dto, result);
    }

    @Test
    void deletePhoneSuspiciousTransferShouldCallRepository() {

        service.deletePhoneSuspiciousTransfer(transferId);
        verify(phoneTransferRepository).deleteById(transferId.longValue());
    }
}