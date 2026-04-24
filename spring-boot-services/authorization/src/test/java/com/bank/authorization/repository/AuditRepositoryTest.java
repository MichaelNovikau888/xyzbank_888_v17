package com.bank.authorization.repository;

import com.bank.authorization.entity.Audit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false"
})
class AuditRepositoryTest {

    @Autowired
    private AuditRepository auditRepository;

    private Audit savedAudit;

    @BeforeEach
    void setUp() {
        Audit audit = new Audit();
        audit.setEntityType("User");
        audit.setCreatedAt(LocalDateTime.parse("2025-03-24T14:11:56"));
        audit.setOperationType("CREATE");
        audit.setCreatedBy("admin");
        audit.setModifiedBy("system");
        audit.setEntityJson("{}");
        audit.setNewEntityJson("{}");

        savedAudit = auditRepository.save(audit);
    }

    @Test
    void testSaveAndFindAudit() {
        Optional<Audit> foundAudit = auditRepository.findById(savedAudit.getId());

        assertTrue(foundAudit.isPresent());
        assertEquals("User", foundAudit.get().getEntityType());
        assertEquals("admin", foundAudit.get().getCreatedBy());
    }

    @Test
    void testFindById() {
        Optional<Audit> foundAudit = auditRepository.findById(savedAudit.getId());

        assertTrue(foundAudit.isPresent(), "Audit should be found");
        assertEquals(savedAudit.getId(), foundAudit.get().getId(), "IDs should match");
        assertEquals("User", foundAudit.get().getEntityType(), "Entity types should match");
        assertEquals("CREATE", foundAudit.get().getOperationType(), "Operation types should match");
    }

    @Test
    void testFindById_NotFound() {
        Optional<Audit> foundAudit = auditRepository.findById(999L);

        assertFalse(foundAudit.isPresent(), "Audit should not be found for non-existent ID");
    }

    @Test
    void testUpdateAudit() {
        savedAudit.setOperationType("UPDATE");
        savedAudit.setModifiedBy("system2");

        Audit updatedAudit = auditRepository.save(savedAudit);
        Optional<Audit> foundAudit = auditRepository.findById(updatedAudit.getId());

        assertTrue(foundAudit.isPresent());
        assertEquals("UPDATE", foundAudit.get().getOperationType());
        assertEquals("system2", foundAudit.get().getModifiedBy());
    }
}
