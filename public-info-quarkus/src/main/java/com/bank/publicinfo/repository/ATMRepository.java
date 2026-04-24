package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.ATM;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Репозиторий банкоматов.
 * Аналог Spring Data: List<ATM> findByBranchId(Long branchId).
 */
@ApplicationScoped
public class ATMRepository implements PanacheRepository<ATM> {

    public List<ATM> findByBranchId(Long branchId) {
        return list("branch.id", branchId);
    }
}
