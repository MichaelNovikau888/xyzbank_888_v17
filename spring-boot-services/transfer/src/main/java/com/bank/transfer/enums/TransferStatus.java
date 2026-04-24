package com.bank.transfer.enums;

/**
 * Жизненный цикл перевода с точки зрения уведомлений.
 */
 /**
 *  CREATED   — перевод сохранён в БД, ожидает антифрод-проверки
 *  REVIEW    — антифрод требует ручной проверки (промежуточный статус)
 *  COMPLETED — антифрод разрешил (ALLOW), перевод исполняется
 *  BLOCKED   — антифрод заблокировал (BLOCK)
 *  CANCELLED — отменён клиентом или системой (до исполнения)
 */
public enum TransferStatus {
    CREATED,
    REVIEW,
    COMPLETED,
    BLOCKED,
    CANCELLED
}
