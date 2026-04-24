package com.bank.antifraud.globalException;

// Extends RuntimeException directly — antifraud uses Spring Data JDBC (not JPA),
// so jakarta.persistence.EntityNotFoundException is not available.
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
