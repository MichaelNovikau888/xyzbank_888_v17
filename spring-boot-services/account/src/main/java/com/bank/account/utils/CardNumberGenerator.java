package com.bank.account.utils;

import com.bank.account.enums.CardType;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates valid 16-digit credit card numbers using the Luhn algorithm.
 */
@Component
public class CardNumberGenerator {

    private final SecureRandom random = new SecureRandom();

 /**
     * Generate a valid 16-digit card number for the given card type.
     * First digit(s) — BIN prefix by payment system.
     * Last digit — Luhn checksum.
 */
    public String generate(CardType cardType) {
        String bin = getBIN(cardType);
        StringBuilder cardNumber = new StringBuilder(bin);

        // Fill digits up to position 15 (16th will be Luhn checksum)
        for (int i = bin.length(); i < 15; i++) {
            cardNumber.append(random.nextInt(10));
        }

        // Append Luhn checksum digit
        int checksum = calculateLuhnChecksum(cardNumber.toString());
        cardNumber.append(checksum);

        return cardNumber.toString();
    }

 /**
     * Bank Identification Number prefix per card type.
 */
    private String getBIN(CardType cardType) {
        return switch (cardType) {
            case VISA       -> "4";
            case MASTERCARD -> "5";
            case MIR        -> "2";
            case MAESTRO    -> "6";
        };
    }

 /**
     * Calculate the Luhn check digit for a partial card number string.
 */
    private int calculateLuhnChecksum(String number) {
        int sum = 0;
        boolean alternate = true;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }
}
