package com.bank.antifraud.mappers;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.model.SuspiciousAccountTransfer;
import com.bank.antifraud.model.SuspiciousCardTransfer;
import com.bank.antifraud.model.SuspiciousPhoneTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SuspiciousTransferMapperTest {

    @Spy
    @InjectMocks
    private SuspiciousTransferMapper mapper = Mappers.getMapper(SuspiciousTransferMapper.class);

    @Test
    void toAccountDTO_shouldMapCorrectly() {
        // Arrange
        SuspiciousAccountTransfer entity = new SuspiciousAccountTransfer();
        entity.setId(1L);
        entity.setAccountTransferId(100L);
        entity.setBlocked(true);
        entity.setSuspicious(true);
        entity.setSuspiciousReason("Large amount");

        // Act
        SuspiciousAccountTransferDto dto = mapper.toAccountDTO(entity);

        // Assert
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals(100L, dto.getAccountTransferId());
        assertTrue(dto.isBlocked());
        assertTrue(dto.isSuspicious());
        assertEquals("Large amount", dto.getSuspiciousReason());
    }

    @Test
    void toPhoneEntity_shouldMapCorrectly() {
        // Arrange
        SuspiciousPhoneTransferDto dto = new SuspiciousPhoneTransferDto();
        dto.setId(2L);
        dto.setPhoneTransferId(200L);
        dto.setBlocked(false);
        dto.setSuspicious(true);
        dto.setSuspiciousReason("Suspicious country");

        // Act
        SuspiciousPhoneTransfer entity = mapper.toPhoneEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(2L, entity.getId());
        assertEquals(200L, entity.getPhoneTransferId());
        assertFalse(entity.isBlocked());
        assertTrue(entity.isSuspicious());
        assertEquals("Suspicious country", entity.getSuspiciousReason());
    }

    @Test
    void toCardDTO_shouldMapCorrectly() {
        // Arrange
        SuspiciousCardTransfer entity = new SuspiciousCardTransfer();
        entity.setId(3L);
        entity.setCardTransferId(300L);
        entity.setBlocked(true);
        entity.setSuspicious(false);
        entity.setSuspiciousReason("Multiple countries");

        // Act
        SuspiciousCardTransferDto dto = mapper.toCardDTO(entity);

        // Assert
        assertNotNull(dto);
        assertEquals(3L, dto.getId());
        assertEquals(300L, dto.getCardTransferId());
        assertTrue(dto.isBlocked());
        assertFalse(dto.isSuspicious());
        assertEquals("Multiple countries", dto.getSuspiciousReason());
    }

    @Test
    void toAccountEntity_shouldMapCorrectly() {
        // Arrange
        SuspiciousAccountTransferDto dto = new SuspiciousAccountTransferDto();
        dto.setId(4L);
        dto.setAccountTransferId(400L);
        dto.setBlocked(false);
        dto.setSuspicious(false);
        dto.setSuspiciousReason("Test reason");

        // Act
        SuspiciousAccountTransfer entity = mapper.toAccountEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(4L, entity.getId());
        assertEquals(400L, entity.getAccountTransferId());
        assertFalse(entity.isBlocked());
        assertFalse(entity.isSuspicious());
        assertEquals("Test reason", entity.getSuspiciousReason());
    }

    @Test
    void toPhoneDTO_shouldMapCorrectly() {
        // Arrange
        SuspiciousPhoneTransfer entity = new SuspiciousPhoneTransfer();
        entity.setId(5L);
        entity.setPhoneTransferId(500L);
        entity.setBlocked(true);
        entity.setSuspicious(true);
        entity.setSuspiciousReason("High risk");

        // Act
        SuspiciousPhoneTransferDto dto = mapper.toPhoneDTO(entity);

        // Assert
        assertNotNull(dto);
        assertEquals(5L, dto.getId());
        assertEquals(500L, dto.getPhoneTransferId());
        assertTrue(dto.isBlocked());
        assertTrue(dto.isSuspicious());
        assertEquals("High risk", dto.getSuspiciousReason());
    }

    @Test
    void toCardEntity_shouldMapCorrectly() {
        // Arrange
        SuspiciousCardTransferDto dto = new SuspiciousCardTransferDto();
        dto.setId(6L);
        dto.setCardTransferId(600L);
        dto.setBlocked(true);
        dto.setSuspicious(true);
        dto.setSuspiciousReason("Stolen card");

        // Act
        SuspiciousCardTransfer entity = mapper.toCardEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(6L, entity.getId());
        assertEquals(600L, entity.getCardTransferId());
        assertTrue(entity.isBlocked());
        assertTrue(entity.isSuspicious());
        assertEquals("Stolen card", entity.getSuspiciousReason());
    }
}
