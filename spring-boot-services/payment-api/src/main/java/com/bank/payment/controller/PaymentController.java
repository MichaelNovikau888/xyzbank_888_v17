package com.bank.payment.controller;

import com.bank.payment.dto.CreatePaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.security.JwtUtil;
import com.bank.payment.service.IdempotencyKeyGenerator;
import com.bank.payment.service.PaymentService;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер платежей.
 */
 /**
 * <p>Безопасность: clientId извлекается из JWT токена (Authorization: Bearer ...),
 * а НЕ из заголовка Client-Id. Это исключает возможность подмены идентификатора
 * клиента на стороне запроса.
 */
 /**
 * <p>Если JWT отсутствует, истёк или подпись некорректна — возвращается 401.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment creation and status API")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService          paymentService;
    private final IdempotencyKeyGenerator keyGenerator;
    private final JwtUtil                 jwtUtil;

 /**
     * Создание платежа с поддержкой идемпотентности.
 */
 /**
     * <p>clientId извлекается из JWT (claim «clientId» или subject).
     * Idempotency-Key генерируется сервером на основе clientId + параметров платежа
     * + 3-секундного временного окна — клиент не передаёт его вручную.
 */
    @PostMapping
    @Operation(
        summary = "Create payment",
        description = "Creates a new payment. clientId is extracted from JWT. " +
                      "Idempotency-Key is generated server-side from clientId + amount + recipient + 3s window."
    )
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Bearer JWT token", required = true)
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreatePaymentRequest request) {

        String clientId = extractClientIdOrUnauthorized(authHeader);
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String idempotencyKey = keyGenerator.generateKey(
                clientId,
                request.getRecipientAccount(),
                request.getAmount(),
                request.getCurrency()
        );

        PaymentResponse response = paymentService.createPayment(clientId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

 /**
     * Получение статуса платежа.
 */
 /**
     * <p>clientId из JWT используется для проверки, что платёж принадлежит
     * именно этому клиенту — см. {@link PaymentService#getPaymentStatus}.
 */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @PathVariable Long paymentId,
            @Parameter(description = "Bearer JWT token", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String clientId = extractClientIdOrUnauthorized(authHeader);
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaymentResponse response = paymentService.getPaymentStatus(clientId, paymentId);
        return ResponseEntity.ok(response);
    }

 /**
     * Получение платежа по идемпотентному ключу.
 */
    @GetMapping("/by-key/{idempotencyKey}")
    @Operation(summary = "Get payment by idempotency key")
    public ResponseEntity<PaymentResponse> getPaymentByKey(
            @PathVariable String idempotencyKey,
            @Parameter(description = "Bearer JWT token", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String clientId = extractClientIdOrUnauthorized(authHeader);
        if (clientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaymentResponse response = paymentService.getPaymentByKey(clientId, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    // ── Private ───────────────────────────────────────────────────────────────

 /**
     * Извлекает clientId из JWT. При любой ошибке логирует и возвращает null
     * (контроллер вернёт 401).
 */
    private String extractClientIdOrUnauthorized(String authHeader) {
        try {
            return jwtUtil.extractClientId(authHeader);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
}
