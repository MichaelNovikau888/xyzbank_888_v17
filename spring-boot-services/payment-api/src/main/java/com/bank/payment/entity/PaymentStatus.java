package com.bank.payment.entity;

public enum PaymentStatus {
    CREATED,        // Платёж создан
    PROCESSING,     // В обработке
    COMPLETED,      // Успешно завершён
    FAILED,         // Ошибка
    CANCELLED       // Отменён
}