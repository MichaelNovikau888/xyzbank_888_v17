package com.bank.profile.kafka.consumer;

import com.bank.profile.dto.ProfileDto;
import com.bank.profile.kafka.producer.ProfileProducer;
import com.bank.profile.metrics.ProfileMetrics;
import com.bank.profile.repository.ProfileRepository;
import com.bank.profile.service.ProfileService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Kafka-консьюмер операций над профилями.
 /
 * Idempotency (Quarkus at-least-once):
 *   CREATE — проверяем по snils (уникальный идентификатор физлица).
 *            Если профиль уже существует — пропускаем.
 *   UPDATE — идемпотентен по природе, но проверяем существование по id.
 *   DELETE — проверяем existsById, пропускаем если уже удалён.
 *   GET    — всегда идемпотентен, проверка не нужна.
 */
@ApplicationScoped
public class ProfileConsumer {

    private static final Logger log = Logger.getLogger(ProfileConsumer.class);

    @Inject ProfileService profileService;
    @Inject ProfileProducer profileProducer;
    @Inject ProfileRepository profileRepository;
    @Inject ProfileMetrics metrics;

    @Incoming("profile-create-in")
    @Blocking
    public void create(ProfileDto dto) {
        log.infof("Kafka [profile.create] received: profileId=%d", dto.getId());

        // ── Idempotency: CREATE ──────────────────────────────────────────────
        if (dto.getSnils() != null && profileRepository.findBySnils(dto.getSnils()).isPresent()) {
            log.warnf("Idempotency: profile with snils=%d already exists, skipping create", dto.getSnils());
            metrics.getProfileIdempotentSkipped().increment();
            return;
        }
        if (dto.getId() != null && profileRepository.findByIdOptional(dto.getId()).isPresent()) {
            log.warnf("Idempotency: profile id=%d already exists, skipping create", dto.getId());
            metrics.getProfileIdempotentSkipped().increment();
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        profileService.create(dto);
        metrics.getProfilesCreated().increment();
        log.infof("Profile created: id=%d", dto.getId());
    }

    @Incoming("profile-update-in")
    @Blocking
    public void update(ProfileDto dto) {
        log.infof("Kafka [profile.update] received: profileId=%d", dto.getId());

        // ── Idempotency: UPDATE ──────────────────────────────────────────────
        if (dto.getId() == null || profileRepository.findByIdOptional(dto.getId()).isEmpty()) {
            log.warnf("Idempotency: profile id=%d not found for update, skipping", dto.getId());
            metrics.getProfileIdempotentSkipped().increment();
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        profileService.update(dto.getId(), dto);
        metrics.getProfilesUpdated().increment();
    }

    @Incoming("profile-delete-in")
    @Blocking
    public void delete(ProfileDto dto) {
        log.infof("Kafka [profile.delete] received: profileId=%d", dto.getId());

        // ── Idempotency: DELETE ──────────────────────────────────────────────
        if (dto.getId() == null || profileRepository.findByIdOptional(dto.getId()).isEmpty()) {
            log.warnf("Idempotency: profile id=%d already deleted or not found, skipping", dto.getId());
            metrics.getProfileIdempotentSkipped().increment();
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        profileService.delete(dto.getId());
        metrics.getProfilesDeleted().increment();
    }

    @Incoming("profile-get-in")
    @Blocking
    public void get(ProfileDto dto) {
        log.infof("Kafka [profile.get] received: profileId=%d", dto.getId());
        // GET всегда идемпотентен — проверка не нужна
        profileProducer.sendGetResponse(profileService.get(dto.getId()));
    }
}
