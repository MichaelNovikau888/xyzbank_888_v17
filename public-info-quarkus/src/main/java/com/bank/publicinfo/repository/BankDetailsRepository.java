package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.BankDetails;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Репозиторий банковских реквизитов.
 * В Quarkus реализуем PanacheRepository вместо JpaRepository.
 * Метод findByBik — аналог Spring Data метода findByBik(Long bik).
 */
@ApplicationScoped
public class BankDetailsRepository implements PanacheRepository<BankDetails> {

    public Optional<BankDetails> findByBik(Long bik) {
        return find("bik", bik).firstResultOptional();
    }
}
