package com.bank.history.repository;

import com.bank.history.entity.History;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class HistoryRepository implements PanacheRepository<History> {

 /**
     * События по имени сервиса с пагинацией.
     * Используется в HistoryServiceImpl.getAuditHistoryByServiceName().
 */
    public List<History> findByServiceName(String serviceName, int page, int size) {
        return find("serviceName = ?1 ORDER BY createdAt DESC", serviceName)
                .page(Page.of(page, size)).list();
    }

 /**
     * События по типу с пагинацией.
     * Используется в HistoryServiceImpl.getAuditHistoryByEventType().
 */
    public List<History> findByEventType(String eventType, int page, int size) {
        return find("eventType = ?1 ORDER BY createdAt DESC", eventType)
                .page(Page.of(page, size)).list();
    }

 /**
     * Последние N событий для дашборда/мониторинга.
     * Используется в HistoryServiceImpl.getRecentEvents().
 */
    public List<History> findRecentEvents(int limit) {
        return find("ORDER BY createdAt DESC").page(0, limit).list();
    }

    public List<History> findByTransferAuditIdPaged(Long transferAuditId, int page, int size) {
        return find("transferAuditId = ?1 ORDER BY createdAt DESC", transferAuditId)
                .page(Page.of(page, size)).list();
    }

    public long countByTransferAuditId(Long transferAuditId) {
        return count("transferAuditId", transferAuditId);
    }

 /**
     * Idempotency: поиск по хэшу содержимого события.
     * Если такое событие уже сохранено — пропускаем дубль.
 */
    public Optional<History> findByContentHash(String contentHash) {
        return find("contentHash", contentHash).firstResultOptional();
    }
}
