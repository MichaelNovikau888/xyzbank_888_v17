package com.bank.account.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Securely hashes CVV codes using BCrypt.
 */
 /**
 * <p>IMPORTANT: Never store CVV in plain text!
 */
@Component
public class CvvHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

 /**
     * Hash a CVV code.
 */
    public String hash(String cvv) {
        return encoder.encode(cvv);
    }

 /**
     * Verify a CVV code against its stored hash.
 */
    public boolean verify(String cvv, String hash) {
        return encoder.matches(cvv, hash);
    }
}
