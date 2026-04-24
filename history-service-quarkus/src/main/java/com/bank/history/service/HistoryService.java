package com.bank.history.service;

import com.bank.history.dto.HistoryDto;
import com.bank.history.dto.PagedResponse;
import com.bank.history.entity.History;

import java.util.List;

/**
 * Сервисный интерфейс для работы с историей событий.
 /
 /**
 * Постраничный вывод реализован через собственный PagedResponse<T>
 * вместо Spring Data Page<T>, которого нет в Quarkus/Panache.
 */
public interface HistoryService {

    void saveHistory(History history);

 /**
     * Idempotency check: существует ли событие с данным content hash?
     * Используется в HistoryKafkaListener для deduplicate при at-least-once delivery.
 */
    boolean existsByContentHash(String contentHash);

    PagedResponse<HistoryDto> getAuditHistory(int page, int size);

    PagedResponse<HistoryDto> getAuditHistoryByServiceName(String serviceName, int page, int size);

    PagedResponse<HistoryDto> getAuditHistoryByEventType(String eventType, int page, int size);

    PagedResponse<HistoryDto> getAuditHistoryByTransferId(Long transferAuditId, int page, int size);

 /**
     * Последние N событий (без пагинации) — для дашбордов и мониторинга.
     * Лимит 1–100 задаётся явно.
 */
    List<HistoryDto> getRecentEvents(int limit);
}
