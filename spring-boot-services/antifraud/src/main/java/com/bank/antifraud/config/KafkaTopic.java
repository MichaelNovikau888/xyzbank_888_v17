package com.bank.antifraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopic {

    // ── Аудит ────────────────────────────────────────────────────────────────

    @Bean
    public NewTopic logsTopic() {
        return TopicBuilder.name("audit.logs").partitions(3).replicas(1).build();
    }

    // ── Топики antifraud ↔ payment-api (уже существует в prod, декларируем явно) ──

    @Bean
    public NewTopic paymentAntifraudCheckTopic() {
        return TopicBuilder.name("payment.antifraud.check").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentAntifraudResponseTopic() {
        return TopicBuilder.name("payment.antifraud.response").partitions(3).replicas(1).build();
    }

    // ── НОВЫЕ топики: antifraud ↔ transfer-service ────────────────────────────

 /**
     * Запросы от transfer-service на антифрод-проверку переводов.
     * 3 партиции: параллельная обработка account/card/phone потоков.
 */
    @Bean
    public NewTopic transferAntifraudCheckTopic() {
        return TopicBuilder.name("transfer.antifraud.check").partitions(3).replicas(1).build();
    }

 /**
     * Ответы antifraud в transfer-service.
     * Partition key = transferId → transfer получает ответ детерминировано.
 */
    @Bean
    public NewTopic transferAntifraudResponseTopic() {
        return TopicBuilder.name("transfer.antifraud.response").partitions(3).replicas(1).build();
    }
}
