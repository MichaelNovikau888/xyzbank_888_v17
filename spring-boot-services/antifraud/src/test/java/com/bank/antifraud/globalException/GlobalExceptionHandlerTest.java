package com.bank.antifraud.globalException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private BindingResult bindingResult;

    @Mock
    private ConstraintViolationException constraintViolationException;

    @Mock
    private HttpMessageNotReadableException httpMessageNotReadableException;

    @Mock
    private EntityNotFoundException entityNotFoundException;

    @Mock
    private Exception genericException;

    @Mock
    private WebRequest webRequest;

    @Mock
    private ConstraintViolation<?> constraintViolation;

    @Mock
    private Path path;

    @Test
    void handleValidationExceptions_shouldReturnValidationErrors() {
        FieldError fieldError = new FieldError("object", "amount", "must be positive");
        when(bindingResult.getAllErrors()).thenReturn(Collections.singletonList(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                exceptionHandler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().message());
        assertEquals("must be positive", response.getBody().errors().get("amount"));
    }

    @Test
    void handleConstraintViolation_shouldReturnConstraintErrors() {
        when(constraintViolation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("transferAmount");
        when(constraintViolation.getMessage()).thenReturn("must be less than 1000000");
        when(constraintViolationException.getConstraintViolations())
                .thenReturn(Set.of(constraintViolation));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                exceptionHandler.handleConstraintViolation(constraintViolationException);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Constraint violation", response.getBody().message());
        assertEquals("must be less than 1000000", response.getBody().errors().get("transferAmount"));
    }

    @Test
    void handleInvalidJson_shouldReturnJsonError() {
        when(httpMessageNotReadableException.getMessage()).thenReturn("Invalid JSON");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                exceptionHandler.handleInvalidJson(httpMessageNotReadableException);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid JSON format", response.getBody().message());
        assertEquals("Malformed JSON request", response.getBody().errors().get("error"));
    }

    @Test
    void handleEntityNotFound_shouldReturnNotFoundError() {
        when(entityNotFoundException.getMessage()).thenReturn("Account not found");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                exceptionHandler.handleEntityNotFound(entityNotFoundException);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Entity not found", response.getBody().message());
        assertEquals("Account not found", response.getBody().errors().get("error"));
    }

    @Test
    void handleAllExceptions_shouldReturnInternalServerError() {
        when(genericException.getMessage()).thenReturn("Unexpected error");
        when(webRequest.getDescription(false)).thenReturn("uri=/api/accounts");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                exceptionHandler.handleAllExceptions(genericException, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().message());
        assertEquals("uri=/api/accounts", response.getBody().errors().get("path"));
    }

    @Test
    void errorResponse_shouldCorrectlyStoreValues() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> errors = new HashMap<>();
        errors.put("field", "error message");

        GlobalExceptionHandler.ErrorResponse errorResponse =
                new GlobalExceptionHandler.ErrorResponse(now, 400, "Bad request", errors);

        assertEquals(now, errorResponse.localDateTime());
        assertEquals(400, errorResponse.status());
        assertEquals("Bad request", errorResponse.message());
        assertEquals("error message", errorResponse.errors().get("field"));
    }
}
