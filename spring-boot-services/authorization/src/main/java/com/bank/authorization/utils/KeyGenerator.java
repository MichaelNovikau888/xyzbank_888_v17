package com.bank.authorization.utils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Утилитарный класс для генерации безопасного случайного ключа.
 * Использует SecureRandom для генерации массива байтов
 * и кодирует его в строку Base64. Использовал его для генерации
 * ключа, который используется для подписи токенов JWT.
 */
public class KeyGenerator {

 /**
     * Генерирует случайный ключ, кодирует его в Base64 и выводит в консоль.
     * Рекомендуемый размер ключа для HS256 — 256 бит (32 байта).
 */
 /**
     * @param args аргументы командной строки (не используются)
 */
    public static void main(String[] args) {
        final SecureRandom secureRandom = new SecureRandom();
        final int keySize = 32;
        final byte[] key = new byte[keySize];
        secureRandom.nextBytes(key);
        final String encodedKey = Base64.getEncoder().encodeToString(key);
        System.out.println(encodedKey);
    }
}
