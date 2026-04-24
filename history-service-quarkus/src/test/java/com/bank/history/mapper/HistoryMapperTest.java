package com.bank.history.mapper;

import com.bank.history.dto.HistoryDto;
import com.bank.history.entity.History;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * Unit-тесты HistoryMapper (MapStruct, componentModel="jakarta").
 */
 /**
 * Проверяем:
 *   1. toDto: все поля entity → dto (включая новые publicBankInfoAuditId, authorizationAuditId)
 *   2. toEntity: все поля dto → entity; contentHash игнорируется (@Mapping ignore=true)
 *   3. null input → null output (MapStruct default)
 */
@QuarkusTest
@DisplayName("HistoryMapper — unit tests")
class HistoryMapperTest {

    @Inject
    HistoryMapper mapper;

    private History buildFullEntity() {
        History h = new History();
        h.setId(1L);
        h.setTransferAuditId(10L);
        h.setProfileAuditId(20L);
        h.setAccountAuditId(30L);
        h.setAntiFraudAuditId(40L);
        h.setPublicBankInfoAuditId(50L);
        h.setAuthorizationAuditId(60L);
        h.setEventType("TRANSFER");
        h.setEventData("{\"amount\":500}");
        h.setServiceName("transfer-service");
        h.setCreatedAt(LocalDateTime.of(2026, 4, 19, 12, 0));
        h.setContentHash("abc123def456");
        return h;
    }

    private HistoryDto buildFullDto() {
        HistoryDto d = new HistoryDto();
        d.setId(1L);
        d.setTransferAuditId(10L);
        d.setProfileAuditId(20L);
        d.setAccountAuditId(30L);
        d.setAntiFraudAuditId(40L);
        d.setPublicBankInfoAuditId(50L);
        d.setAuthorizationAuditId(60L);
        d.setEventType("TRANSFER");
        d.setEventData("{\"amount\":500}");
        d.setServiceName("transfer-service");
        d.setCreatedAt(LocalDateTime.of(2026, 4, 19, 12, 0));
        return d;
    }

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toDto: все поля маппируются корректно")
    void toDto_mapsAllFields() {
        History entity = buildFullEntity();

        HistoryDto dto = mapper.toDto(entity);

        Assertions.assertThat(dto.getId()).isEqualTo(1L);
        Assertions.assertThat(dto.getTransferAuditId()).isEqualTo(10L);
        Assertions.assertThat(dto.getProfileAuditId()).isEqualTo(20L);
        Assertions.assertThat(dto.getAccountAuditId()).isEqualTo(30L);
        Assertions.assertThat(dto.getAntiFraudAuditId()).isEqualTo(40L);
        Assertions.assertThat(dto.getPublicBankInfoAuditId()).isEqualTo(50L);
        Assertions.assertThat(dto.getAuthorizationAuditId()).isEqualTo(60L);
        Assertions.assertThat(dto.getEventType()).isEqualTo("TRANSFER");
        Assertions.assertThat(dto.getEventData()).isEqualTo("{\"amount\":500}");
        Assertions.assertThat(dto.getServiceName()).isEqualTo("transfer-service");
        Assertions.assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 19, 12, 0));
    }

    @Test
    @DisplayName("toDto: null entity → null dto")
    void toDto_null_returnsNull() {
        Assertions.assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    @DisplayName("toDto: частично заполненный entity — null-поля остаются null в dto")
    void toDto_partialEntity_nullFieldsPreserved() {
        History h = new History();
        h.setId(5L);
        h.setEventType("AUDIT");
        // остальные поля null

        HistoryDto dto = mapper.toDto(h);

        Assertions.assertThat(dto.getId()).isEqualTo(5L);
        Assertions.assertThat(dto.getEventType()).isEqualTo("AUDIT");
        Assertions.assertThat(dto.getTransferAuditId()).isNull();
        Assertions.assertThat(dto.getPublicBankInfoAuditId()).isNull();
        Assertions.assertThat(dto.getAuthorizationAuditId()).isNull();
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toEntity: все поля dto маппируются в entity")
    void toEntity_mapsAllFields() {
        HistoryDto dto = buildFullDto();

        History entity = mapper.toEntity(dto);

        Assertions.assertThat(entity.getId()).isEqualTo(1L);
        Assertions.assertThat(entity.getTransferAuditId()).isEqualTo(10L);
        Assertions.assertThat(entity.getPublicBankInfoAuditId()).isEqualTo(50L);
        Assertions.assertThat(entity.getAuthorizationAuditId()).isEqualTo(60L);
        Assertions.assertThat(entity.getEventType()).isEqualTo("TRANSFER");
        Assertions.assertThat(entity.getServiceName()).isEqualTo("transfer-service");
        Assertions.assertThat(entity.getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 4, 19, 12, 0));
    }

    @Test
    @DisplayName("toEntity: contentHash игнорируется (@Mapping ignore=true) — остаётся null")
    void toEntity_contentHash_isIgnored() {
        HistoryDto dto = buildFullDto();

        History entity = mapper.toEntity(dto);

        // contentHash вычисляется в HistoryKafkaListener, не маппится из DTO
        Assertions.assertThat(entity.getContentHash()).isNull();
    }

    @Test
    @DisplayName("toEntity: null dto → null entity")
    void toEntity_null_returnsNull() {
        Assertions.assertThat(mapper.toEntity(null)).isNull();
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trip toDto→toEntity: все поля сохраняются (кроме contentHash)")
    void roundTrip_preservesAllFields() {
        History original = buildFullEntity();

        HistoryDto dto    = mapper.toDto(original);
        History   result  = mapper.toEntity(dto);

        Assertions.assertThat(result.getId()).isEqualTo(original.getId());
        Assertions.assertThat(result.getTransferAuditId()).isEqualTo(original.getTransferAuditId());
        Assertions.assertThat(result.getPublicBankInfoAuditId())
                .isEqualTo(original.getPublicBankInfoAuditId());
        Assertions.assertThat(result.getAuthorizationAuditId())
                .isEqualTo(original.getAuthorizationAuditId());
        Assertions.assertThat(result.getEventType()).isEqualTo(original.getEventType());
        Assertions.assertThat(result.getServiceName()).isEqualTo(original.getServiceName());
        // contentHash не маппится из DTO → null в результате
        Assertions.assertThat(result.getContentHash()).isNull();
    }
}
