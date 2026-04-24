package com.bank.account.service;

import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.dto.CreditCardDto;
import com.bank.account.entity.Account;
import com.bank.account.entity.CreditCard;
import com.bank.account.enums.CardStatus;
import com.bank.account.enums.CardType;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.exception.custom_exceptions.ValidationException;
import com.bank.account.mapper.CreditCardMapper;
import com.bank.account.producers.CardEventProducer;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CreditCardRepository;
import com.bank.account.utils.CardNumberGenerator;
import com.bank.account.utils.CvvHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardMapper creditCardMapper;
    @Mock
    private CardNumberGenerator cardNumberGenerator;
    @Mock
    private CvvHasher cvvHasher;
    @Mock
    private CardEventProducer cardEventProducer;

    @InjectMocks
    private CreditCardServiceImpl creditCardService;

    private CreateCreditCardRequest request;
    private Account account;
    private CreditCard creditCard;
    private CreditCardDto creditCardDto;

    @BeforeEach
    void setUp() {
        request = new CreateCreditCardRequest();
        request.setAccountId(1L);
        request.setCardholderName("John Doe");
        request.setCardType(CardType.VISA);
        request.setExpiryDate(LocalDate.now().plusYears(3));
        request.setCvv("123");

        account = new Account();
        account.setId(1L);
        account.setMoney(BigDecimal.valueOf(10000));

        creditCard = new CreditCard();
        creditCard.setId(1L);
        creditCard.setCardNumber("4111111111111111");
        creditCard.setAccountId(1L);
        creditCard.setCardholderName("John Doe");
        creditCard.setCardType(CardType.VISA);
        creditCard.setStatus(CardStatus.ACTIVE);
        creditCard.setExpiryDate(LocalDate.now().plusYears(3));

        creditCardDto = CreditCardDto.builder()
                .id(1L)
                .maskedCardNumber("4111 **** **** 1111")
                .accountId(1L)
                .cardholderName("John Doe")
                .build();
    }

    @Test
    @DisplayName("Should create credit card successfully")
    void shouldCreateCreditCard() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(creditCardRepository.countByAccountIdAndStatus(1L, CardStatus.ACTIVE)).thenReturn(2L);
        when(creditCardMapper.toEntity(request)).thenReturn(creditCard);
        when(cardNumberGenerator.generate(CardType.VISA)).thenReturn("4111111111111111");
        when(creditCardRepository.existsByCardNumber(any())).thenReturn(false);
        when(cvvHasher.hash("123")).thenReturn("hashed_cvv");
        when(creditCardRepository.save(any(CreditCard.class))).thenReturn(creditCard);
        when(creditCardMapper.toDto(creditCard)).thenReturn(creditCardDto);

        CreditCardDto result = creditCardService.create(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("4111 **** **** 1111", result.getMaskedCardNumber());
        verify(creditCardRepository).save(any(CreditCard.class));
        verify(cardEventProducer).sendCardCreated(any(CreditCard.class));
    }

    @Test
    @DisplayName("Should throw exception when account not found")
    void shouldThrowWhenAccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> creditCardService.create(request));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when max cards limit reached")
    void shouldThrowWhenMaxCardsLimitReached() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(creditCardRepository.countByAccountIdAndStatus(1L, CardStatus.ACTIVE)).thenReturn(5L);

        assertThrows(ValidationException.class, () -> creditCardService.create(request));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should block card successfully")
    void shouldBlockCard() {
        when(creditCardRepository.findById(1L)).thenReturn(Optional.of(creditCard));
        when(creditCardRepository.save(any(CreditCard.class))).thenReturn(creditCard);
        when(creditCardMapper.toDto(creditCard)).thenReturn(creditCardDto);

        CreditCardDto result = creditCardService.block(1L);

        assertNotNull(result);
        verify(creditCardRepository).save(any(CreditCard.class));
        verify(cardEventProducer).sendCardBlocked(any(CreditCard.class));
    }

    @Test
    @DisplayName("Should throw exception when blocking already blocked card")
    void shouldThrowWhenBlockingAlreadyBlockedCard() {
        creditCard.setStatus(CardStatus.BLOCKED);
        when(creditCardRepository.findById(1L)).thenReturn(Optional.of(creditCard));

        assertThrows(ValidationException.class, () -> creditCardService.block(1L));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should unblock card successfully")
    void shouldUnblockCard() {
        creditCard.setStatus(CardStatus.BLOCKED);
        when(creditCardRepository.findById(1L)).thenReturn(Optional.of(creditCard));
        when(creditCardRepository.save(any(CreditCard.class))).thenReturn(creditCard);
        when(creditCardMapper.toDto(creditCard)).thenReturn(creditCardDto);

        CreditCardDto result = creditCardService.unblock(1L);

        assertNotNull(result);
        verify(cardEventProducer).sendCardUnblocked(any(CreditCard.class));
    }

    @Test
    @DisplayName("Should throw when card not found on getById")
    void shouldThrowWhenCardNotFoundOnGet() {
        when(creditCardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> creditCardService.getById(99L));
    }

    @Test
    @DisplayName("Should delete card successfully")
    void shouldDeleteCard() {
        when(creditCardRepository.existsById(1L)).thenReturn(true);

        creditCardService.delete(1L);

        verify(creditCardRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Should throw when deleting non-existent card")
    void shouldThrowWhenDeletingNonExistentCard() {
        when(creditCardRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> creditCardService.delete(99L));
    }
}
