package com.bank.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import com.bank.payment.dto.CreatePaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.entity.Payment;
import com.bank.payment.entity.PaymentStatus;
import com.bank.payment.event.PaymentCreatedEvent;
import com.bank.payment.exception.PaymentNotFoundException;
import com.bank.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private CreatePaymentRequest validRequest;
    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        validRequest = new CreatePaymentRequest(
            "40817810099910004312",
            new BigDecimal("10000.00"),
            "RUB",
            "Test payment"
        );

        savedPayment = new Payment();
        savedPayment.setId(1L);
        savedPayment.setClientId("CLIENT_123");
        savedPayment.setIdempotencyKey("idem_test_123");
        savedPayment.setRecipientAccount("40817810099910004312");
        savedPayment.setAmount(new BigDecimal("10000.00"));
        savedPayment.setCurrency("RUB");
        savedPayment.setStatus(PaymentStatus.CREATED);
        savedPayment.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Создание нового платежа - успешно")
    void createPayment_NewPayment_Success() {
        // Given
        String clientId = "CLIENT_123";
        String idempotencyKey = "idem_test_123";
        
        when(paymentRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
            .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(savedPayment);

        // When
        PaymentResponse response = paymentService.createPayment(clientId, idempotencyKey, validRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(response.getCurrency()).isEqualTo("RUB");
        assertThat(response.getStatus()).isEqualTo("CREATED");

        // Проверяем, что платёж сохранён
        verify(paymentRepository, times(1)).save(any(Payment.class));
        
        // Проверяем, что событие опубликовано в Kafka
        ArgumentCaptor<PaymentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        verify(kafkaTemplate, times(1)).send(eq("payment.created"), eq("1"), eventCaptor.capture());
        
        PaymentCreatedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getPaymentId()).isEqualTo(1L);
        assertThat(capturedEvent.getAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("Идемпотентность - повторный запрос возвращает существующий платёж")
    void createPayment_DuplicateIdempotencyKey_ReturnsExisting() {
        // Given
        String clientId = "CLIENT_123";
        String idempotencyKey = "idem_test_123";
        
        when(paymentRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
            .thenReturn(Optional.of(savedPayment));

        // When
        PaymentResponse response = paymentService.createPayment(clientId, idempotencyKey, validRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        
        // Проверяем, что НЕ создали новый платёж
        verify(paymentRepository, never()).save(any(Payment.class));
        
        // Проверяем, что НЕ публиковали событие в Kafka
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Валидация - отрицательная сумма выбрасывает исключение")
    void createPayment_NegativeAmount_ThrowsException() {
        // Given
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest(
            "40817810099910004312",
            new BigDecimal("-100.00"),
            "RUB",
            "Invalid"
        );

        // When & Then
        assertThatThrownBy(() -> 
            paymentService.createPayment("CLIENT_123", "idem_123", invalidRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount must be positive");
        
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Валидация - сумма превышает лимит")
    void createPayment_AmountExceedsLimit_ThrowsException() {
        // Given
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest(
            "40817810099910004312",
            new BigDecimal("20000000.00"),  // > 10 млн
            "RUB",
            "Too large"
        );

        // When & Then
        assertThatThrownBy(() -> 
            paymentService.createPayment("CLIENT_123", "idem_123", invalidRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum limit");
    }

    @Test
    @DisplayName("Получение статуса платежа - успешно")
    void getPaymentStatus_ExistingPayment_Success() {
        // Given
        Long paymentId = 1L;
        String clientId = "CLIENT_123";
        
        when(paymentRepository.findByIdAndClientId(paymentId, clientId))
            .thenReturn(Optional.of(savedPayment));

        // When
        PaymentResponse response = paymentService.getPaymentStatus(clientId, paymentId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Получение статуса - платёж не найден")
    void getPaymentStatus_PaymentNotFound_ThrowsException() {
        // Given
        Long paymentId = 999L;
        String clientId = "CLIENT_123";
        
        when(paymentRepository.findByIdAndClientId(paymentId, clientId))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> 
            paymentService.getPaymentStatus(clientId, paymentId))
            .isInstanceOf(PaymentNotFoundException.class)
            .hasMessageContaining("Payment not found: 999");
    }

    @Test
    @DisplayName("Получение платежа по idempotency key - успешно")
    void getPaymentByKey_ExistingKey_Success() {
        // Given
        String clientId = "CLIENT_123";
        String idempotencyKey = "idem_test_123";
        
        when(paymentRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
            .thenReturn(Optional.of(savedPayment));

        // When
        PaymentResponse response = paymentService.getPaymentByKey(clientId, idempotencyKey);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
    }
}