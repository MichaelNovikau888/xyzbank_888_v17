package com.bank.authorization.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    private static final int ROLE_LENGTH = 40;
    private static final int PASSWORD_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "role", length = ROLE_LENGTH, nullable = false)
    private String role;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "password", length = PASSWORD_LENGTH, nullable = false)
    private String password;

    // ── Self-service registration fields (добавлены в Phase 2) ───────────────

    /** Email клиента — уникальный идентификатор при self-service регистрации. */
    @Column(name = "email", length = 255, unique = true)
    private String email;

    /** Полное имя клиента. */
    @Column(name = "full_name", length = 255)
    private String fullName;

    /** Номер телефона в международном формате. */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

 /**
     * Статус жизненного цикла пользователя.
     * PENDING_VERIFICATION → ACTIVE → BLOCKED
     * Хранится как строка для расширяемости без миграций.
 */
    @Column(name = "status", length = 30)
    private String status;
}
