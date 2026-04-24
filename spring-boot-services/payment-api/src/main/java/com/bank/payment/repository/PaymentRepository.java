package com.bank.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.bank.payment.entity.Payment;
import com.bank.payment.entity.PaymentStatus;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);
    Optional<Payment> findByIdAndClientId(Long id, String clientId);

    /** Используется Gauge-метрикой: количество незавершённых платежей */
    long countByStatus(PaymentStatus status);
}
