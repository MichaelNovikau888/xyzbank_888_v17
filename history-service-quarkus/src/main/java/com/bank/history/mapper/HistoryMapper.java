package com.bank.history.mapper;

import com.bank.history.dto.HistoryDto;
import com.bank.history.entity.History;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct маппер History ↔ HistoryDto.
 /
 * Отличие от Spring-варианта: componentModel = "jakarta" (или "jakartaee")
 * вместо "spring". Quarkus использует CDI (Jakarta), а не Spring DI.
 * Это позволяет @Inject маппер как обычный CDI-бин.
/
 * Аналог Spring: @Mapper(componentModel = "spring")
 */
@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface HistoryMapper {

    HistoryDto toDto(History history);

 /**
     * contentHash вычисляется в HistoryKafkaListener (SHA-256),
     * поэтому игнорируем его при маппинге из DTO → Entity.
 */
    @Mapping(target = "contentHash", ignore = true)
    History toEntity(HistoryDto historyDto);
}
