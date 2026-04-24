package com.bank.transfer.aspect;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Аспект аудита для transfer-service.
 */
 /**
 * <p><strong>История изменений:</strong><br>
 * Ранее этот аспект вызывал {@code TransferProducer.send*()} через {@code @Before},
 * что создавало дублирующий Kafka-round-trip внутри одного сервиса:
 * <pre>
 *   AuditAspect (@Before) → TransferProducer → transfer.account/card/phone
 *   → TransferConsumer (тот же сервис!) → ждёт topicAccountDetailsGet (мёртвый)
 * </pre>
 * Это был антипаттерн: асинхронный round-trip через Kafka внутри одного процесса,
 * плюс мёртвое ожидание ответа на топик, который никто не продюсирует.
 */
 /**
 * <p><strong>Актуальная архитектура:</strong><br>
 * {@code TransferServiceImpl.save*()} сам записывает перевод в БД и кладёт
 * три события в outbox (в одной транзакции):
 * <ol>
 *   <li>{@code suspicious-transfers.create} — legacy, удаляется</li>
 *   <li>{@code transfer.antifraud.check} — антифрод через {@link com.bank.transfer.antifraud.AntifraudOutboxHelper}</li>
 *   <li>{@code transfer.notification} — уведомление через {@link com.bank.transfer.notification.TransferNotificationOutboxHelper}</li>
 * </ol>
 * История пишется через {@link com.bank.transfer.outbox.HistoryOutboxHelper} → {@code transfer.events}.
 */
 /**
 * <p>Аспект оставлен для структурного логирования после успешного сохранения.
 * Kafka-вызовы убраны: они дублировали логику и создавали мёртвые топики.
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {

    @AfterReturning(
            pointcut = "execution(* com.bank.transfer.service.TransferServiceImpl.saveAccountTransfer(..)) && args(dto)",
            argNames = "dto")
    public void logAccountTransferSaved(AccountTransferDto dto) {
        log.info("[AUDIT] Account transfer saved: accountNumber={} amount={}",
                 dto.getAccountNumber(), dto.getAmount());
    }

    @AfterReturning(
            pointcut = "execution(* com.bank.transfer.service.TransferServiceImpl.saveCardTransfer(..)) && args(dto)",
            argNames = "dto")
    public void logCardTransferSaved(CardTransferDto dto) {
        log.info("[AUDIT] Card transfer saved: cardNumber={} amount={}",
                 dto.getCardNumber(), dto.getAmount());
    }

    @AfterReturning(
            pointcut = "execution(* com.bank.transfer.service.TransferServiceImpl.savePhoneTransfer(..)) && args(dto)",
            argNames = "dto")
    public void logPhoneTransferSaved(PhoneTransferDto dto) {
        log.info("[AUDIT] Phone transfer saved: phoneNumber={} amount={}",
                 dto.getPhoneNumber(), dto.getAmount());
    }
}
