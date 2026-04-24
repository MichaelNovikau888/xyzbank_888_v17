package com.bank.profile.kafka.consumer;

import com.bank.profile.dto.AccountDetailsDto;
import com.bank.profile.kafka.producer.AccountDetailsProducer;
import com.bank.profile.metrics.ProfileMetrics;
import com.bank.profile.repository.AccountDetailsRepository;
import com.bank.profile.service.AccountDetailsService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Kafka-консьюмер операций над AccountDetails.
 */
 /**
 * Idempotency: проверяем existsById перед CREATE / DELETE.
 * UPDATE идемпотентен по природе, но проверяем существование.
 */
@ApplicationScoped
public class AccountDetailsConsumer {

    private static final Logger log = Logger.getLogger(AccountDetailsConsumer.class);

    @Inject AccountDetailsService accountDetailsService;
    @Inject AccountDetailsProducer accountDetailsProducer;
    @Inject AccountDetailsRepository accountDetailsRepository;
    @Inject ProfileMetrics metrics;

    @Incoming("account-details-create-in")
    @Blocking
    public void create(AccountDetailsDto dto) {
        log.infof("Kafka [account.details.create] received: id=%d", dto.getId());

        // ── Idempotency: CREATE ──────────────────────────────────────────────
        if (dto.getId() != null && accountDetailsRepository.findByIdOptional(dto.getId()).isPresent()) {
            log.warnf("Idempotency: AccountDetails id=%d already exists, skipping", dto.getId());
            metrics.getAccountDetailsIdempotentSkipped().increment();
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        accountDetailsService.create(dto);
        metrics.getAccountDetailsCreated().increment();
    }

    @Incoming("account-details-update-in")
    @Blocking
    public void update(AccountDetailsDto dto) {
        log.infof("Kafka [account.details.update] received: id=%d", dto.getId());

        if (dto.getId() == null || accountDetailsRepository.findByIdOptional(dto.getId()).isEmpty()) {
            log.warnf("Idempotency: AccountDetails id=%d not found for update, skipping", dto.getId());
            metrics.getAccountDetailsIdempotentSkipped().increment();
            return;
        }

        accountDetailsService.update(dto.getId(), dto);
        metrics.getAccountDetailsUpdated().increment();
    }

    @Incoming("account-details-delete-in")
    @Blocking
    public void delete(AccountDetailsDto dto) {
        log.infof("Kafka [account.details.delete] received: id=%d", dto.getId());

        // ── Idempotency: DELETE ──────────────────────────────────────────────
        if (dto.getId() == null || accountDetailsRepository.findByIdOptional(dto.getId()).isEmpty()) {
            log.warnf("Idempotency: AccountDetails id=%d already deleted, skipping", dto.getId());
            metrics.getAccountDetailsIdempotentSkipped().increment();
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        accountDetailsService.delete(dto.getId());
        metrics.getAccountDetailsDeleted().increment();
    }

    @Incoming("account-details-get-in")
    @Blocking
    public void get(AccountDetailsDto dto) {
        log.infof("Kafka [account.details.get] received: id=%d", dto.getId());
        accountDetailsProducer.sendGetResponse(accountDetailsService.get(dto.getId()));
    }
}
