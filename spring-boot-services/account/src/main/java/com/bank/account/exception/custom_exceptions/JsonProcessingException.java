package com.bank.account.exception.custom_exceptions;

public class JsonProcessingException extends com.fasterxml.jackson.core.JsonProcessingException {
    public JsonProcessingException(String message) {
        super(message);
    }

    public JsonProcessingException(String message, Throwable cause) {
        super(message, cause);

    }
}
