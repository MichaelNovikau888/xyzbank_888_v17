package com.bank.account.consumers;

import com.bank.account.config.KafkaTopicsConfig;
import com.bank.account.dto.AccountDto;
import com.bank.account.exception.KafkaErrorSender;
import com.bank.account.metrics.AccountMetrics;
import com.bank.account.producers.AccountProducer;
import com.bank.account.repository.AccountRepository;
import com.bank.account.security.TokenValidationService;
import com.bank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka-консьюмер команд над счетами.
 */
 /**
 * Idempotency (at-least-once delivery):
 */
 /**
 *   CREATE — проверяем existsAccountByAccountNumber перед созданием.
 *            accountNumber — натуральный бизнес-ключ, уникален по смыслу.
 *            Если счёт уже есть — возвращаем существующий без дублирования.
 */
 /**
 *   UPDATE — идемпотентен по своей природе: повторное применение тех же
 *            полей не меняет итоговое состояние. Просто выполняем ещё раз.
 */
 /**
 *   DELETE — проверяем existsById перед удалением: повторный DELETE на
 *            несуществующий счёт не должен бросать исключение.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCommandConsumer {

    @Value("${kafka.topics.error-logs}")
    private String topicError;

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final AccountProducer accountProducer;
    private final KafkaErrorSender kafkaErrorSender;
    private final KafkaTopicsConfig kafkaTopicsConfig;
    private final TokenValidationService tokenValidationService;
    private final AccountMetrics accountMetrics;

    @KafkaListener(topics = "${kafka.topics.account-create}",
            containerFactory = "accountKafkaListenerContainerFactory")
    public void handleCreateAccount(@Payload AccountDto accountDto,
                                    @Header("Authorization") String jwtToken) {
        try {
            tokenValidationService.validateJwtOrThrow(jwtToken);

            // ── Idempotency guard: CREATE ────────────────────────────────────
            // Kafka at-least-once: сообщение может прийти повторно при ребалансировке
            // или перезапуске. Проверяем по натуральному ключу accountNumber.
            if (accountDto.getAccountNumber() != null
                    && accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber())) {
                log.warn("Idempotency: account with accountNumber={} already exists, skipping create",
                        accountDto.getAccountNumber());
                accountMetrics.getAccountIdempotentSkipped().increment();
                // Отправляем уже существующий счёт как ответ — consumer ожидает результат
                var existing = accountRepository.findAccountByAccountNumber(accountDto.getAccountNumber());
                if (existing != null) {
                    accountProducer.sendExternalEvent(
                            kafkaTopicsConfig.getExternalAccountCreate(),
                            mapToDto(existing, accountDto)
                    );
                }
                return;
            }
            // ────────────────────────────────────────────────────────────────

            final AccountDto responseAccount = accountService.createNewAccount(accountDto);
            accountProducer.sendExternalEvent(kafkaTopicsConfig.getExternalAccountCreate(), responseAccount);
            accountMetrics.getAccountsCreated().increment();
            log.info("handleCreateAccount completed: accountNumber={}", accountDto.getAccountNumber());

        } catch (Exception e) {
            log.error("handleCreateAccount failed: ", e);
            kafkaErrorSender.sendError(e, topicError);
        }
    }

    @KafkaListener(topics = "${kafka.topics.account-update}",
            containerFactory = "accountKafkaListenerContainerFactory")
    public void handleUpdateAccount(@Payload AccountDto accountDto,
                                    @Header("Authorization") String jwtToken) {
        try {
            tokenValidationService.validateJwtOrThrow(jwtToken);

            // UPDATE идемпотентен: повторное применение тех же данных — допустимо.
            // Дополнительная проверка: убеждаемся что счёт существует,
            // иначе логируем и пропускаем (не создаём заново).
            if (accountDto.getId() != null && !accountRepository.existsById(accountDto.getId())) {
                log.warn("Idempotency: account id={} not found for update, skipping", accountDto.getId());
                return;
            }

            final AccountDto responseAccount = accountService.updateCurrentAccount(accountDto.getId(), accountDto);
            accountProducer.sendExternalEvent(kafkaTopicsConfig.getExternalAccountUpdate(), responseAccount);
            log.info("handleUpdateAccount completed: id={}", accountDto.getId());

        } catch (Exception e) {
            log.error("handleUpdateAccount failed: ", e);
            kafkaErrorSender.sendError(e, topicError);
        }
    }

    @KafkaListener(topics = "${kafka.topics.account-delete}",
            containerFactory = "longKafkaListenerContainerFactory")
    public void handleDeleteAccount(@Payload Long accountId,
                                    @Header("Authorization") String jwtToken) {
        try {
            tokenValidationService.validateJwtOrThrow(jwtToken);

            // ── Idempotency guard: DELETE ────────────────────────────────────
            // Если счёт уже удалён — повторное сообщение не должно бросать исключение.
            if (!accountRepository.existsById(accountId)) {
                log.warn("Idempotency: account id={} already deleted or never existed, skipping",
                        accountId);
                accountProducer.sendTextMessage(kafkaTopicsConfig.getExternalAccountDelete(),
                        String.format("Account with id: %s already deleted (idempotent)", accountId));
                return;
            }
            // ────────────────────────────────────────────────────────────────

            accountService.deleteAccount(accountId);
            accountProducer.sendTextMessage(kafkaTopicsConfig.getExternalAccountDelete(),
                    String.format("Account with id: %s successfully deleted", accountId));
            log.info("handleDeleteAccount completed: id={}", accountId);

        } catch (Exception e) {
            log.error("handleDeleteAccount failed: ", e);
            kafkaErrorSender.sendError(e, topicError);
        }
    }

    // helper: строим ответный DTO из существующей entity + входящего DTO
    private AccountDto mapToDto(com.bank.account.entity.Account account, AccountDto incoming) {
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        dto.setAccountNumber(account.getAccountNumber());
        dto.setMoney(account.getMoney());
        dto.setNegativeBalance(account.isNegativeBalance());
        dto.setPassportId(account.getPassportId());
        dto.setBankDetailsId(account.getBankDetailsId());
        dto.setProfileId(account.getProfileId());
        return dto;
    }
}
