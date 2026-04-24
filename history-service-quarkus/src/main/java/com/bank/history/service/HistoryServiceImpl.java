package com.bank.history.service;

import com.bank.history.dto.HistoryDto;
import com.bank.history.dto.PagedResponse;
import com.bank.history.entity.History;
import com.bank.history.mapper.HistoryMapper;
import com.bank.history.repository.HistoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Реализация сервиса истории событий.
 /
 /**
 * Все запросы к БД делегируются в HistoryRepository — сервис не содержит
 * inline HQL/JPQL. Это позволяет:
 *   1. Тестировать запросы в одном месте (репозиторий).
 *   2. Устранить предупреждение IDEA «метод репозитория не используется».
 *   3. Следовать принципу единственной ответственности.
 */
@ApplicationScoped
public class HistoryServiceImpl implements HistoryService {

    private static final Logger log = Logger.getLogger(HistoryServiceImpl.class);

    @Inject HistoryRepository historyRepository;
    @Inject HistoryMapper     historyMapper;

    @Override
    @Transactional
    public void saveHistory(History history) {
        historyRepository.persist(history);
        log.infof("History saved: id=%d, service=%s, type=%s",
                history.getId(), history.getServiceName(), history.getEventType());
    }

    @Override
    public boolean existsByContentHash(String contentHash) {
        return historyRepository.findByContentHash(contentHash).isPresent();
    }

 /**
     * Постраничный вывод всей истории (новые первые).
 */
    @Override
    public PagedResponse<HistoryDto> getAuditHistory(int page, int size) {
        validatePagination(page, size);
        List<HistoryDto> content = historyRepository
                .find("ORDER BY createdAt DESC")
                .page(page, size)
                .list()
                .stream()
                .map(historyMapper::toDto)
                .collect(Collectors.toList());
        long total = historyRepository.count();
        log.debugf("getAuditHistory: page=%d, size=%d, total=%d", page, size, total);
        return new PagedResponse<>(content, page, size, total);
    }

 /**
     * События по имени сервиса — делегирует в {@link HistoryRepository#findByServiceName}.
 */
    @Override
    public PagedResponse<HistoryDto> getAuditHistoryByServiceName(String serviceName,
                                                                   int page, int size) {
        validatePagination(page, size);
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name must not be blank");
        }

        long total = historyRepository.count("serviceName", serviceName);
        if (total == 0) {
            throw new EntityNotFoundException("No history found for service: " + serviceName);
        }

        List<HistoryDto> content = historyRepository
                .findByServiceName(serviceName, page, size)
                .stream()
                .map(historyMapper::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(content, page, size, total);
    }

 /**
     * События по типу — делегирует в {@link HistoryRepository#findByEventType}.
 */
    @Override
    public PagedResponse<HistoryDto> getAuditHistoryByEventType(String eventType,
                                                                 int page, int size) {
        validatePagination(page, size);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }

        long total = historyRepository.count("eventType", eventType);
        if (total == 0) {
            throw new EntityNotFoundException("No history found for event type: " + eventType);
        }

        List<HistoryDto> content = historyRepository
                .findByEventType(eventType, page, size)
                .stream()
                .map(historyMapper::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(content, page, size, total);
    }

 /**
     * События по ID аудита перевода — делегирует в
     * {@link HistoryRepository#findByTransferAuditIdPaged} и
     * {@link HistoryRepository#countByTransferAuditId}.
 */
    @Override
    public PagedResponse<HistoryDto> getAuditHistoryByTransferId(Long transferAuditId,
                                                                  int page, int size) {
        validatePagination(page, size);
        if (transferAuditId == null) {
            throw new IllegalArgumentException("transferAuditId cannot be null");
        }

        long total = historyRepository.countByTransferAuditId(transferAuditId);
        if (total == 0) {
            throw new EntityNotFoundException(
                    "No history found for transferAuditId: " + transferAuditId);
        }

        List<HistoryDto> content = historyRepository
                .findByTransferAuditIdPaged(transferAuditId, page, size)
                .stream()
                .map(historyMapper::toDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(content, page, size, total);
    }

 /**
     * Последние N событий — делегирует в {@link HistoryRepository#findRecentEvents}.
     * Используется эндпоинтом GET /api/history/recent?limit=N.
 */
    @Override
    public List<HistoryDto> getRecentEvents(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return historyRepository
                .findRecentEvents(limit)
                .stream()
                .map(historyMapper::toDto)
                .collect(Collectors.toList());
    }

    private void validatePagination(int page, int size) {
        if (page < 0)   throw new IllegalArgumentException("Page index must not be less than zero");
        if (size < 1)   throw new IllegalArgumentException("Page size must not be less than one");
        if (size > 200) throw new IllegalArgumentException("Page size must not exceed 200");
    }
}
