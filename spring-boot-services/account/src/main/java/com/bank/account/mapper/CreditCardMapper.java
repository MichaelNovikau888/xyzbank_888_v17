package com.bank.account.mapper;

import com.bank.account.dto.CreditCardDto;
import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.entity.CreditCard;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for CreditCard entity.
 */
@Mapper(componentModel = "spring")
public interface CreditCardMapper {

 /**
     * Convert entity to DTO with masked card number.
 */
    @Mapping(target = "maskedCardNumber", expression = "java(maskCardNumber(card.getCardNumber()))")
    CreditCardDto toDto(CreditCard card);

 /**
     * Convert create request to entity.
     * cardNumber and cvvHash are set manually by the service.
 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cardNumber", ignore = true)
    @Mapping(target = "cvvHash", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CreditCard toEntity(CreateCreditCardRequest request);

 /**
     * Mask card number for security.
     * Example: 1234567890123456 -> 1234 **** **** 3456
 */
    default String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "Invalid card number";
        }
        return cardNumber.substring(0, 4) + " **** **** " + cardNumber.substring(12);
    }
}
