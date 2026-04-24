package com.bank.profile.exception;

import jakarta.persistence.EntityExistsException;

/**
 * Выбрасывается при попытке создать сущность с неуникальным полем
 * (например, дублирующийся email, ИНН или СНИЛС).
 */
public class EntityNotUniqueException extends EntityExistsException {

    public final String className;
    public final String fieldName;

    public EntityNotUniqueException(String className, String fieldName) {
        super();
        this.className = className;
        this.fieldName = fieldName;
    }
}
