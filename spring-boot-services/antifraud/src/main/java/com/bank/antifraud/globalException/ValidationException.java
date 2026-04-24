package com.bank.antifraud.globalException;

// Extends RuntimeException — unchecked, не требует объявления в сигнатуре метода.
// Ранее наследовался от javax.xml.bind.ValidationException (checked, удалён в Java 17+).
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
