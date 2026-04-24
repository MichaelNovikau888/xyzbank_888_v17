package com.bank.account.exception;

import com.bank.account.exception.custom_exceptions.DataAccessException;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.exception.custom_exceptions.SecurityException;
import com.bank.account.exception.custom_exceptions.ValidationException;
import com.bank.account.exception.error_dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class GlobalExceptionHandler {
    public ErrorResponse handleException(Exception exception) {
        final ErrorResponse errorResponse;

        if (exception instanceof EntityNotFoundException) {
            errorResponse = new ErrorResponse("ENTITY_NOT_FOUND", exception.getMessage());
            log.error("Entity not found: {}", exception.getMessage());
        } else if (exception instanceof ValidationException) {
            errorResponse = new ErrorResponse("VALIDATION_ERROR", exception.getMessage());
            log.error("Validation error: {}", exception.getMessage());
        } else if (exception instanceof DataAccessException) {
            errorResponse = new ErrorResponse("DATA_ACCESS_ERROR", exception.getMessage());
            log.error("Data access error: {}", exception.getMessage());
        } else if (exception instanceof com.bank.account.exception.custom_exceptions.IllegalArgumentException) {
            errorResponse = new ErrorResponse("ILLEGAL_ARGUMENT", exception.getMessage());
            log.error("Illegal argument: {}", exception.getMessage());
        } else if (exception instanceof JsonProcessingException) {
            errorResponse = new ErrorResponse("JSON_PARSING_ERROR", exception.getMessage());
            log.error("Json parsing error: {}", exception.getMessage());
        } else if (exception instanceof SecurityException) {
            errorResponse = new ErrorResponse("SECURITY_EXCEPTION", exception.getMessage());
            log.error("JWT is invalid: {}", exception.getMessage());
        } else {
            errorResponse = new ErrorResponse("INTERNAL_ERROR", exception.getMessage());
            log.error("Unexpected error: {}", exception.getMessage());
        }
        return errorResponse;
    }

    /**
     * Маппинг исключения → стандартный код {@link com.bank.account.exception.error_dto.ErrorEvent}.
     * Используется {@link KafkaErrorSender} для формирования единой схемы.
     */
    public String toErrorCode(Exception exception) {
        if (exception instanceof EntityNotFoundException)   return "NOT_FOUND";
        if (exception instanceof ValidationException)       return "VALIDATION_ERROR";
        if (exception instanceof DataAccessException)       return "INTERNAL_ERROR";
        if (exception instanceof com.bank.account.exception.custom_exceptions.IllegalArgumentException)
                                                            return "VALIDATION_ERROR";
        if (exception instanceof JsonProcessingException)   return "INTERNAL_ERROR";
        if (exception instanceof SecurityException)         return "UNAUTHORIZED";
        return "INTERNAL_ERROR";
    }
}
