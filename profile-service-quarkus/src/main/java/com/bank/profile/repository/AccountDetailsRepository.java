package com.bank.profile.repository;

import com.bank.profile.entity.AccountDetails;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * В Spring: JpaRepository<AccountDetails, Long> с @Repository.
 * В Quarkus: PanacheRepository<AccountDetails> с @ApplicationScoped.
 * Базовые методы findById, persist, delete и т.д. — унаследованы от Panache.
 */
@ApplicationScoped
public class AccountDetailsRepository implements PanacheRepository<AccountDetails> {
}
