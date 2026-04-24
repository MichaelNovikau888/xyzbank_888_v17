package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.License;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Репозиторий лицензий.
 * Аналоги Spring Data:
 *   findLicensesByBankDetails(BankDetails bd) → list("bankDetails", bd)
 *   deleteLicensesByBankDetailsId(Long id)    → delete("bankDetails.id", id)
 */
@ApplicationScoped
public class LicenseRepository implements PanacheRepository<License> {

    public List<License> findByBankDetails(BankDetails bankDetails) {
        return list("bankDetails", bankDetails);
    }

    @Transactional
    public long deleteByBankDetailsId(Long bankDetailsId) {
        return delete("bankDetails.id", bankDetailsId);
    }
}
