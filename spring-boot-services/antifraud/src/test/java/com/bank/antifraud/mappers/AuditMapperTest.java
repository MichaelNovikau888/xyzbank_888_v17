package com.bank.antifraud.mappers;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.model.Audit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class AuditMapperTest {

    private final AuditMapper mapper = Mappers.getMapper(AuditMapper.class);

    @Test
    void toAuditDTO_shouldMapAllFieldsCorrectly() {
        // Arrange
        Audit audit = createTestAudit(1L, "SuspiciousTransfer", "CREATE",
                "admin", "system", LocalDateTime.now(),
                LocalDateTime.now().plusHours(1), "{\"id\":1}", "{\"id\":1,\"status\":\"ACTIVE\"}");

        // Act
        AuditDto dto = mapper.toAuditDTO(audit);

        // Assert
        assertNotNull(dto);
        assertEquals(audit.getId(), dto.getId());
        assertEquals(audit.getEntityType(), dto.getEntityType());
        assertEquals(audit.getOperationType(), dto.getOperationType());
        assertEquals(audit.getCreatedBy(), dto.getCreatedBy());
        assertEquals(audit.getModifiedBy(), dto.getModifiedBy());
        assertEquals(audit.getCreatedAt(), dto.getCreatedAt());
        assertEquals(audit.getModifiedAt(), dto.getModifiedAt());
        assertEquals(audit.getNewEntityJson(), dto.getNewEntityJson());
        assertEquals(audit.getEntityJson(), dto.getEntityJson());
    }

    @Test
    void toAuditDTO_shouldHandleNullValues() {
        // Arrange
        Audit audit = new Audit();

        // Act
        AuditDto dto = mapper.toAuditDTO(audit);

        // Assert
        assertNotNull(dto);
        assertEquals(0L, dto.getId());
        assertNull(dto.getEntityType());
        assertNull(dto.getOperationType());
        assertNull(dto.getCreatedBy());
        assertNull(dto.getModifiedBy());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getModifiedAt());
        assertNull(dto.getNewEntityJson());
        assertNull(dto.getEntityJson());
    }

    @Test
    void toAuditEntity_shouldMapAllFieldsCorrectly() {
        // Arrange
        AuditDto dto = createTestAuditDto(2L, "Account", "UPDATE",
                "user", "admin", LocalDateTime.now().minusDays(1),
                LocalDateTime.now(), "{\"balance\":1000}", "{\"balance\":1500}");
        // Act
        Audit entity = mapper.toAuditEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getEntityType(), entity.getEntityType());
        assertEquals(dto.getOperationType(), entity.getOperationType());
        assertEquals(dto.getCreatedBy(), entity.getCreatedBy());
        assertEquals(dto.getModifiedBy(), entity.getModifiedBy());
        assertEquals(dto.getCreatedAt(), entity.getCreatedAt());
        assertEquals(dto.getModifiedAt(), entity.getModifiedAt());
        assertEquals(dto.getNewEntityJson(), entity.getNewEntityJson());
        assertEquals(dto.getEntityJson(), entity.getEntityJson());
    }

    @Test
    void toAuditEntity_shouldHandleNullValues() {
        // Arrange
        AuditDto dto = new AuditDto();

        // Act
        Audit entity = mapper.toAuditEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(0L, dto.getId());
        assertNull(entity.getEntityType());
        assertNull(entity.getOperationType());
        assertNull(entity.getCreatedBy());
        assertNull(entity.getModifiedBy());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getModifiedAt());
        assertNull(entity.getNewEntityJson());
        assertNull(entity.getEntityJson());
    }

    @Test
    void toAuditDTO_shouldMapEntityToDtoCorrectly() {
        // Arrange
        Audit originalEntity = createTestAudit(3L, "Card", "DELETE",
                "system", null, LocalDateTime.now().minusHours(3),
                null, null, "{\"id\":3}");

        // Act
        AuditDto dto = mapper.toAuditDTO(originalEntity);

        // Assert
        assertNotNull(dto);
        assertEquals(originalEntity.getId(), dto.getId());
        assertEquals(originalEntity.getEntityType(), dto.getEntityType());
        assertEquals(originalEntity.getOperationType(), dto.getOperationType());
        assertEquals(originalEntity.getCreatedBy(), dto.getCreatedBy());
        assertEquals(originalEntity.getModifiedBy(), dto.getModifiedBy());
        assertEquals(originalEntity.getCreatedAt(), dto.getCreatedAt());
        assertEquals(originalEntity.getModifiedAt(), dto.getModifiedAt());
        assertEquals(originalEntity.getNewEntityJson(), dto.getNewEntityJson());
        assertEquals(originalEntity.getEntityJson(), dto.getEntityJson());
    }

    @Test
    void toAuditEntity_shouldMapDtoToEntityCorrectly() {
        // Arrange
        AuditDto dto = createTestAuditDto(3L, "Card", "DELETE",
                "system", null, LocalDateTime.now().minusHours(3),
                null, null, "{\"id\":3}");
        // Act
        Audit entity = mapper.toAuditEntity(dto);

        // Assert
        assertNotNull(entity);
        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getEntityType(), entity.getEntityType());
        assertEquals(dto.getOperationType(), entity.getOperationType());
        assertEquals(dto.getCreatedBy(), entity.getCreatedBy());
        assertEquals(dto.getModifiedBy(), entity.getModifiedBy());
        assertEquals(dto.getCreatedAt(), entity.getCreatedAt());
        assertEquals(dto.getModifiedAt(), entity.getModifiedAt());
        assertEquals(dto.getNewEntityJson(), entity.getNewEntityJson());
        assertEquals(dto.getEntityJson(), entity.getEntityJson());
    }

    private Audit createTestAudit(Long id, String entityType, String operationType,
                                  String createdBy, String modifiedBy,
                                  LocalDateTime createdAt, LocalDateTime modifiedAt,
                                  String newEntityJson, String entityJson) {
        Audit audit = new Audit();
        audit.setId(id);
        audit.setEntityType(entityType);
        audit.setOperationType(operationType);
        audit.setCreatedBy(createdBy);
        audit.setModifiedBy(modifiedBy);
        audit.setCreatedAt(createdAt);
        audit.setModifiedAt(modifiedAt);
        audit.setNewEntityJson(newEntityJson);
        audit.setEntityJson(entityJson);
        return audit;
    }

    private AuditDto createTestAuditDto(Long id, String entityType, String operationType,
                                        String createdBy, String modifiedBy,
                                        LocalDateTime createdAt, LocalDateTime modifiedAt,
                                        String newEntityJson, String entityJson) {
        AuditDto dto = new AuditDto();
        dto.setId(id);
        dto.setEntityType(entityType);
        dto.setOperationType(operationType);
        dto.setCreatedBy(createdBy);
        dto.setModifiedBy(modifiedBy);
        dto.setCreatedAt(createdAt);
        dto.setModifiedAt(modifiedAt);
        dto.setNewEntityJson(newEntityJson);
        dto.setEntityJson(entityJson);
        return dto;
    }
}
