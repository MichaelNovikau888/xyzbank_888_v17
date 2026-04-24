package com.bank.account.repository;

import com.bank.account.entity.CreditCard;
import com.bank.account.enums.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CreditCard} entity.
 */
@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

    /** Find credit card by card number. */
    Optional<CreditCard> findByCardNumber(String cardNumber);

    /** Find all cards for a specific account. */
    List<CreditCard> findByAccountId(Long accountId);

    /** Find all cards for an account with a given status. */
    List<CreditCard> findByAccountIdAndStatus(Long accountId, CardStatus status);

    /** Check if card number already exists. */
    boolean existsByCardNumber(String cardNumber);

    /** Find all expired active cards. */
    @Query("SELECT c FROM CreditCard c WHERE c.expiryDate < :date AND c.status = 'ACTIVE'")
    List<CreditCard> findExpiredCards(@Param("date") LocalDate date);

    /** Count active cards for an account. */
    long countByAccountIdAndStatus(Long accountId, CardStatus status);
}
