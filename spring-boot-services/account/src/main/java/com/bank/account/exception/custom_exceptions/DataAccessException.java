package com.bank.account.exception.custom_exceptions;

public class DataAccessException extends org.springframework.dao.DataAccessException {
    public DataAccessException(String message) {
        super(message);
    }
}
