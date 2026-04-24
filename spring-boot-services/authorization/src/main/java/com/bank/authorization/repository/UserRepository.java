package com.bank.authorization.repository;

import com.bank.authorization.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProfileId(Long profileIdLong);

    /** Используется при self-service регистрации для поиска по email. */
    Optional<User> findByEmail(String email);

    /** Проверка уникальности email перед регистрацией. */
    boolean existsByEmail(String email);
}
