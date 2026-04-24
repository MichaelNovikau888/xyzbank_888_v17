package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.Certificate;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Репозиторий сертификатов.
 * Аналоги Spring Data:
 *   findCertificatesByBankDetails(BankDetails bd) → list("bankDetails", bd)
 *   deleteCertificateByBankDetailsId(Long id)     → delete("bankDetails.id", id)
 */
@ApplicationScoped
public class CertificateRepository implements PanacheRepository<Certificate> {

    public List<Certificate> findByBankDetails(BankDetails bankDetails) {
        return list("bankDetails", bankDetails);
    }

    @Transactional
    public long deleteByBankDetailsId(Long bankDetailsId) {
        return delete("bankDetails.id", bankDetailsId);
    }
}
