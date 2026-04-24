package com.bank.authorization.service;

import com.bank.authorization.dto.UserRegistrationRequest;
import com.bank.authorization.entity.Role;
import com.bank.authorization.entity.User;
import com.bank.authorization.event.UserRegisteredEvent;
import com.bank.authorization.outbox.AuthOutboxHelper;
import com.bank.authorization.repository.UserRepository;
import jakarta.persistence.EntityExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Сервис самостоятельной регистрации клиента (self-service registration).
 */
 /**
 * <p>Контракт метода {@link #registerUser}:
 * <ol>
 *   <li>Проверяет, что email ещё не зарегистрирован.</li>
 *   <li>Создаёт {@link User} со статусом {@code PENDING_VERIFICATION}
 *       и ролью {@code ROLE_USER}.</li>
 *   <li>В той же транзакции кладёт событие {@code UserRegistered}
 *       в outbox → топик {@code auth.user.registered}.</li>
 * </ol>
 */
 /**
 * <p>Примечание об архитектуре: текущая {@link User} entity хранит
 * {@code profileId} (Long) — связь с profile-service. При self-service
 * регистрации profile ещё не создан, поэтому {@code profileId} временно
 * равен {@code 0L}; profile-service создаст профиль по событию
 * {@code auth.user.registered} и сообщит обратно свой ID.
 * В будущем это поле можно сделать nullable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthOutboxHelper outboxHelper;

 /**
     * Регистрирует нового клиента.
 */
 /**
     * @param request данные регистрации
     * @return созданный {@link User}
     * @throws EntityExistsException если email уже зарегистрирован
 */
    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        // 1. Проверяем уникальность email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EntityExistsException(
                    "Email already registered: " + request.getEmail());
        }

        // 2. Создаём пользователя
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.ROLE_USER.getValue());
        user.setStatus(UserStatus.PENDING_VERIFICATION.name());
        user.setProfileId(0L); // временно; обновится когда profile-service ответит

        User saved = userRepository.save(user);

        // 3. Публикуем событие через outbox (в той же транзакции — at-least-once)
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.getFullName())
                .phoneNumber(saved.getPhoneNumber())
                .registeredAt(LocalDateTime.now())
                .build();

        outboxHelper.enqueue(
                "auth.user.registered",
                String.valueOf(saved.getId()),
                "UserRegistered",
                event
        );

        log.info("User registered: id={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }

 /**
     * Статусы жизненного цикла пользователя.
     * Вынесены во вложенный enum чтобы не создавать отдельный файл
     * до появления полноценного user lifecycle.
 */
    public enum UserStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        BLOCKED
    }
}
