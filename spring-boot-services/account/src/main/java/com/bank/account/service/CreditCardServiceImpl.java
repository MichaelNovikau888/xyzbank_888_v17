package com.bank.account.service;

import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.dto.CreditCardDto;
import com.bank.account.dto.UpdateCardLimitRequest;
import com.bank.account.entity.Account;
import com.bank.account.entity.CreditCard;
import com.bank.account.enums.CardStatus;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.exception.custom_exceptions.ValidationException;
import com.bank.account.mapper.CreditCardMapper;
import com.bank.account.producers.CardEventProducer;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CreditCardRepository;
import com.bank.account.utils.CardNumberGenerator;
import com.bank.account.utils.CvvHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/*
 // Implementation of {@link CreditCardService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditCardServiceImpl implements CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final AccountRepository accountRepository;
    private final CreditCardMapper creditCardMapper;
    private final CardNumberGenerator cardNumberGenerator;
    private final CvvHasher cvvHasher;
    private final CardEventProducer cardEventProducer;

    /** Maximum number of active cards per account. */
    private static final int MAX_CARDS_PER_ACCOUNT = 5;

    @Override
    @Transactional
    public CreditCardDto create(CreateCreditCardRequest request) {
        log.info("Creating credit card for account: {}", request.getAccountId());

        // Validate account exists
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + request.getAccountId()));

        // Check maximum cards limit
        long activeCardsCount = creditCardRepository
                .countByAccountIdAndStatus(request.getAccountId(), CardStatus.ACTIVE);
        if (activeCardsCount >= MAX_CARDS_PER_ACCOUNT) {
            throw new ValidationException(
                    "Maximum number of cards (" + MAX_CARDS_PER_ACCOUNT + ") reached for this account");
        }

        // Build card entity from request
        CreditCard card = creditCardMapper.toEntity(request);

        // Generate unique Luhn-valid card number
        String cardNumber;
        do {
            cardNumber = cardNumberGenerator.generate(request.getCardType());
        } while (creditCardRepository.existsByCardNumber(cardNumber));
        card.setCardNumber(cardNumber);

        // Hash CVV — never store plain text!
        card.setCvvHash(cvvHasher.hash(request.getCvv()));

        CreditCard saved = creditCardRepository.save(card);

        // Publish Kafka event
        cardEventProducer.sendCardCreated(saved);

        log.info("Credit card created. ID: {}, Masked: {}",
                saved.getId(), creditCardMapper.toDto(saved).getMaskedCardNumber());

        return creditCardMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditCardDto getById(Long id) {
        log.debug("Fetching credit card by ID: {}", id);
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found: " + id));
        return creditCardMapper.toDto(card);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditCardDto getByCardNumber(String cardNumber) {
        log.debug("Fetching credit card by number: {}", maskForLog(cardNumber));
        CreditCard card = creditCardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));
        return creditCardMapper.toDto(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditCardDto> getByAccountId(Long accountId) {
        log.debug("Fetching credit cards for account: {}", accountId);
        return creditCardRepository.findByAccountId(accountId)
                .stream()
                .map(creditCardMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CreditCardDto block(Long id) {
        log.info("Blocking credit card: {}", id);
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found: " + id));

        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new ValidationException("Card is already blocked");
        }

        card.block();
        CreditCard updated = creditCardRepository.save(card);
        cardEventProducer.sendCardBlocked(updated);

        log.info("Credit card blocked: {}", id);
        return creditCardMapper.toDto(updated);
    }

    @Override
    @Transactional
    public CreditCardDto unblock(Long id) {
        log.info("Unblocking credit card: {}", id);
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found: " + id));

        card.unblock(); // throws IllegalStateException if expired
        CreditCard updated = creditCardRepository.save(card);
        cardEventProducer.sendCardUnblocked(updated);

        log.info("Credit card unblocked: {}", id);
        return creditCardMapper.toDto(updated);
    }

    @Override
    @Transactional
    public CreditCardDto updateLimit(Long id, UpdateCardLimitRequest request) {
        log.info("Updating limits for credit card: {}", id);
        CreditCard card = creditCardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found: " + id));

        if (request.getDailyLimit() != null && request.getMonthlyLimit() != null
                && request.getDailyLimit().compareTo(request.getMonthlyLimit()) > 0) {
            throw new ValidationException("Daily limit cannot exceed monthly limit");
        }

        if (request.getDailyLimit() != null) {
            card.setDailyLimit(request.getDailyLimit());
        }
        if (request.getMonthlyLimit() != null) {
            card.setMonthlyLimit(request.getMonthlyLimit());
        }

        CreditCard updated = creditCardRepository.save(card);
        cardEventProducer.sendCardLimitChanged(updated);

        log.info("Credit card limits updated: {}", id);
        return creditCardMapper.toDto(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting credit card: {}", id);
        if (!creditCardRepository.existsById(id)) {
            throw new EntityNotFoundException("Credit card not found: " + id);
        }
        creditCardRepository.deleteById(id);
        log.info("Credit card deleted: {}", id);
    }

    /** Mask card number for safe logging. */
    private String maskForLog(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
