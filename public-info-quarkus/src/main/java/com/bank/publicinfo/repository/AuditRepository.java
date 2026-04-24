package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.Audit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий аудита.
 * Аналоги Spring Data:
 *   findByEntityJson(String json)       → find("entityJson", json).firstResultOptional()
 *   findAuditByEntityType(String type)  → find("entityType", type).firstResultOptional()
 *   findAllByEntityJson(String json)    → list("entityJson", json)
 */
@ApplicationScoped
public class AuditRepository implements PanacheRepository<Audit> {

    public Optional<Audit> findByEntityJson(String entityJson) {
        return find("entityJson", entityJson).firstResultOptional();
    }

    public Optional<Audit> findByEntityType(String entityType) {
        return find("entityType", entityType).firstResultOptional();
    }

    public List<Audit> findAllByEntityJson(String entityJson) {
        return list("entityJson", entityJson);
    }
}
