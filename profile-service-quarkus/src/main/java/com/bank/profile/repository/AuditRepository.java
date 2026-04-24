package com.bank.profile.repository;

import com.bank.profile.entity.Audit;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * В Spring был метод findByEntityTypeAndEntityJsonContains(String, String).
 * В Panache эквивалент — JPQL-запрос через find().
 */
@ApplicationScoped
public class AuditRepository implements PanacheRepository<Audit> {

    public Optional<Audit> findByEntityTypeAndEntityJsonContains(String entityType, String substring) {
        return find("entityType = ?1 AND entityJson LIKE ?2", entityType, "%" + substring + "%")
                .firstResultOptional();
    }
}
