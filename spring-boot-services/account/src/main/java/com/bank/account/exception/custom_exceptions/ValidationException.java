package com.bank.account.exception.custom_exceptions;

// Extends RuntimeException — unchecked, не требует объявления в сигнатуре метода.
// Ранее наследовался от javax.xml.bind.ValidationException (checked) — это вызывало
// "Unhandled exception" в CreditCardServiceImpl и других местах.
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
