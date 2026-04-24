package com.bank.account.service;

import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.dto.CreditCardDto;
import com.bank.account.dto.UpdateCardLimitRequest;

import java.util.List;

/**
 * Service interface for credit card operations.
 */
public interface CreditCardService {

    CreditCardDto create(CreateCreditCardRequest request);

    CreditCardDto getById(Long id);

    CreditCardDto getByCardNumber(String cardNumber);

    List<CreditCardDto> getByAccountId(Long accountId);

    CreditCardDto block(Long id);

    CreditCardDto unblock(Long id);

    CreditCardDto updateLimit(Long id, UpdateCardLimitRequest request);

    void delete(Long id);
}
