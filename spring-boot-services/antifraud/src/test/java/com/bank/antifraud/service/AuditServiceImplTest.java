package com.bank.antifraud.service;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.globalException.DataAccessException;
import com.bank.antifraud.mappers.AuditMapper;
import com.bank.antifraud.model.Audit;
import com.bank.antifraud.repository.AuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private AuditMapper auditMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditServiceImpl auditService;

    private Audit audit;
    private AuditDto auditDto;

    @BeforeEach
    void setUp() {
        audit = new Audit();
        audit.setId(1L); // Long, not int
        audit.setEntityType("SuspiciousCardTransfer");
        audit.setOperationType("CREATE");
        audit.setCreatedBy("system");
        audit.setCreatedAt(LocalDateTime.now());
        audit.setNewEntityJson("{}");
        audit.setEntityJson("{}");

        auditDto = new AuditDto();
        auditDto.setId(1L); // Long, not int
        auditDto.setEntityType("SuspiciousCardTransfer");
        auditDto.setOperationType("CREATE");
        auditDto.setCreatedBy("system");
        auditDto.setNewEntityJson("{}");
        auditDto.setEntityJson("{}");
    }

    @Test
    void createAudit_Success() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(auditRepository.save(any(Audit.class))).thenReturn(audit);
        when(auditMapper.toAuditDTO(any(Audit.class))).thenReturn(auditDto);

        AuditDto result = auditService.createAudit("CREATE", "SuspiciousCardTransfer",
                "system", new Object(), null);

        assertNotNull(result);
        assertEquals("SuspiciousCardTransfer", result.getEntityType());
        verify(auditRepository, times(1)).save(any(Audit.class));
    }

    @Test
    void createAudit_JsonProcessingException() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);

        assertThrows(RuntimeException.class, () ->
                auditService.createAudit("CREATE", "SuspiciousCardTransfer",
                        "system", new Object(), null));
    }

    @Test
    void updateAudit_Success() {
        when(auditRepository.findById(1L)).thenReturn(Optional.of(audit)); // Long literal
        when(auditRepository.save(any(Audit.class))).thenReturn(audit);
        when(auditMapper.toAuditDTO(any(Audit.class))).thenReturn(auditDto);

        AuditDto result = auditService.updateAudit(1L, auditDto);

        assertNotNull(result);
        verify(auditRepository, times(1)).save(audit);
    }

    @Test
    void updateAudit_NotFound() {
        when(auditRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                auditService.updateAudit(1L, auditDto));
    }

    @Test
    void getAuditById_Success() {
        when(auditRepository.findById(1L)).thenReturn(Optional.of(audit));
        when(auditMapper.toAuditDTO(audit)).thenReturn(auditDto);

        AuditDto result = auditService.getAuditById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getAuditById_NotFound() {
        when(auditRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                auditService.getAuditById(1L));
    }

    @Test
    void getAllAudits_Success() {
        when(auditRepository.findAll()).thenReturn(Collections.singletonList(audit));
        when(auditMapper.toAuditDTO(audit)).thenReturn(auditDto);

        List<AuditDto> result = auditService.getAllAudits();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void getAllAudits_DataAccessException() {
        when(auditRepository.findAll()).thenThrow(new DataAccessException("DB error"));

        assertThrows(DataAccessException.class, () ->
                auditService.getAllAudits());
    }

    @Test
    void deleteAudit_Success() {
        doNothing().when(auditRepository).deleteById(1L); // Long literal

        auditService.deleteAudit(1L);

        verify(auditRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteAudit_Exception() {
        doThrow(new RuntimeException()).when(auditRepository).deleteById(1L);

        assertThrows(RuntimeException.class, () ->
                auditService.deleteAudit(1L));
    }
}
