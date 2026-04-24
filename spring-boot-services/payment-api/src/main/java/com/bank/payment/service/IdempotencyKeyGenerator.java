package com.bank.payment.service;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Генерирует защищённый детерминированный идемпотент-ключ на стороне сервера.
 */
 /**
 * <h3>Алгоритм: SipHash-24</h3>
 * <p>SipHash-24 создан Жан-Филиппом Оманом (создатель AES) и Дэниелом Бернштейном
 * специально для защиты хеш-таблиц и идентификаторов.
 * В отличие от SHA-256, он не криптографически стойкий к коллизиям,
 * но использует <b>секретный ключ</b> — без него хеш невозможно воспроизвести.
 */
 /**
 * <h3>Почему не SHA-256?</h3>
 * <ul>
 *   <li>SHA-256 (~600 нс) — избыточен: нам не нужна криптостойкость к коллизиям,
 *       нам нужна непредсказуемость без знания ключа.</li>
 *   <li>SipHash-24 (~150 нс) — в 4 раза быстрее, при этом даёт защиту:
 *       зная хеш и входные данные, восстановить k0/k1 вычислительно невозможно.</li>
 *   <li>При 100 000 RPS экономия: (600 - 150) нс x 100 000 = 45 мс CPU/сек.</li>
 * </ul>
 */
 /**
 * <h3>Защита от утечки данных</h3>
 * <p>Конкатенация вида "client123:40817...:1000:RUB:567890" раскрывает
 * все реквизиты платежа при компрометации БД или логов. SipHash превращает
 * это в "a3f7c2d8e9b14f05" — 16-символьную строку без смысла для атакующего.
 */
 /**
 * <h3>Секретный ключ</h3>
 * <p>k0 и k1 задаются через application.yml и в продакшене
 * подставляются из Vault / K8s Secret. Смена ключа инвалидирует все старые
 * записи в БД — менять только при ротации с миграцией данных.
 */
 /**
 * <h3>Математика временного окна</h3>
 * <pre>
 *   System.currentTimeMillis() / 3000 -&gt; номер 3-секундного блока
 */
 /**
 *   t=0    ms -&gt; блок 0  |
 *   t=1000 ms -&gt; блок 0  | одинаковый хеш -&gt; дубль обнаружен
 *   t=2999 ms -&gt; блок 0  |
 *   t=3000 ms -&gt; блок 1  &lt;- новый блок -&gt; новый ключ
 * </pre>
 */
@Service
public class IdempotencyKeyGenerator {

    /** Длина временного окна: два запроса внутри окна считаются дублями. */
    private static final long WINDOW_MS = 3_000L;

    private final HashFunction sipHash;

 /**
     * @param k0 первая половина 128-битного секретного ключа (из application.yml)
     * @param k1 вторая половина 128-битного секретного ключа (из application.yml)
 */
    public IdempotencyKeyGenerator(
            @Value("${payment.idempotency.sip-hash-k0}") long k0,
            @Value("${payment.idempotency.sip-hash-k1}") long k1) {
        this.sipHash = Hashing.sipHash24(k0, k1);
    }

 /**
     * Генерирует защищённый идемпотент-ключ для платежа.
 */
 /**
     * @param clientId         идентификатор клиента
     * @param recipientAccount номер счёта получателя
     * @param amount           сумма платежа
     * @param currency         валюта (например, "RUB")
     * @return 16-символьная hex-строка (SipHash-24 с секретным ключом),
     *         не раскрывающая исходные параметры платежа
 */
    public String generateKey(String clientId,
                              String recipientAccount,
                              BigDecimal amount,
                              String currency) {

        long timeSlot = System.currentTimeMillis() / WINDOW_MS;

        // Разделитель \u0000 исключает атаки склейки:
        // "a:b"+"c" и "a"+"b:c" дадут разные хеши.
        String raw = clientId + "\u0000" +
                     recipientAccount + "\u0000" +
                     amount.toPlainString() + "\u0000" +
                     currency + "\u0000" +
                     timeSlot;

        return sipHash.hashString(raw, StandardCharsets.UTF_8).toString();
    }
}
