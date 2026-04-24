package com.bank.notification.service;

import com.bank.notification.entity.NotificationRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Сервис сохранения записей об уведомлениях для финальных статусов.
 */
 /**
 * Финальные статусы платежей:  COMPLETED, FAILED, CANCELLED
 * Финальные статусы переводов: COMPLETED, BLOCKED, CANCELLED
 */
 /**
 * BLOCKED добавлен в финальные т.к. это конечное состояние перевода —
 * аналог FAILED для платежей (antifraud заблокировал, исполнение не будет).
 */
@ApplicationScoped
public class NotificationRecordService {

    private static final Logger LOG = Logger.getLogger(NotificationRecordService.class);

    private static final Set<String> FINAL_STATUSES =
            Set.of("COMPLETED", "FAILED", "CANCELLED", "BLOCKED");

 /**
     * Сохранить запись в БД если статус финальный.
     * Возвращает true если запись была сохранена.
 */
    @Transactional
    public boolean saveIfFinal(Long entityId,
                                String clientId,
                                String status,
                                BigDecimal amount,
                                String currency,
                                String recipientAccount,
                                String reason) {
        if (!FINAL_STATUSES.contains(status)) {
            return false;
        }

        NotificationRecord record = new NotificationRecord(
                entityId, clientId, status,
                amount, currency, recipientAccount, reason
        );
        record.persist();

        LOG.infof("NotificationRecord saved: entity_id=%d client_id=%s status=%s",
                  entityId, clientId, status);
        return true;
    }

    public boolean isFinalStatus(String status) {
        return FINAL_STATUSES.contains(status);
    }
}
