package com.bank.transfer.service;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.AuditDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;

import java.util.List;

/**
 * Сервис аудита переводов.
 */
 /**
 * <p><b>Статус:</b> методы auditAccountTransfer/auditCardTransfer/auditPhoneTransfer
 * сохранены для обратной совместимости, но в продуктовом потоке НЕ вызываются.
 * Фактический аудит идёт через Outbox-паттерн:
 * {@code TransferServiceImpl → HistoryOutboxHelper → transfer.events → history-service}.
 */
 /**
 * <p>Метод {@link #getAuditHistory()} используется REST-эндпоинтом
 * {@code AuditController} для чтения локальной таблицы audit.
 */
 /**
 * <p>TODO: при следующем рефакторинге рассмотреть удаление audit-методов
 * или замену на прямой вызов из {@code AuditController} без отдельного сервис-слоя.
 */
public interface AuditService {
    List<AuditDto> getAuditHistory();

    void auditAccountTransfer(AccountTransferDto dto);
    void auditCardTransfer(CardTransferDto dto);
    void auditPhoneTransfer(PhoneTransferDto dto);
}