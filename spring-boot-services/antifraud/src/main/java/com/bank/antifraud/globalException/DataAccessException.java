package com.bank.antifraud.globalException;

public class DataAccessException extends org.springframework.dao.DataAccessException {
    public DataAccessException(String message) {
        super(message);
    }
}

