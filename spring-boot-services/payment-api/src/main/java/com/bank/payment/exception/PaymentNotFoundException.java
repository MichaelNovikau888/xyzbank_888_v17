package com.bank.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment not found: " + paymentId);
    }

    public PaymentNotFoundException(String idempotencyKey) {
        super("Payment not found with idempotency key: " + idempotencyKey);
    }
}