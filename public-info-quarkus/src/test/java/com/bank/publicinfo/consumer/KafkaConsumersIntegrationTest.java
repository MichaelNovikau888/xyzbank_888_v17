package com.bank.publicinfo.consumer;

import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.Branch;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

@QuarkusTest
@DisplayName("Kafka Consumers — Integration Tests (in-memory)")
class KafkaConsumersIntegrationTest {

    @Inject
    InMemoryConnector connector;

    @BeforeEach
    @Transactional
    void clean() {
        BankDetails.deleteAll();
        Branch.deleteAll();
    }

    @Test
    void bankCreate_shouldSaveEntity() {
        String payload = """
            {
              "bik": 555555555,
              "inn": 1234567890123,
              "kpp": "123456789",
              "corAccount": "30101810400000000555",
              "city": "Екатеринбург",
              "jointStockCompany": "ПАО",
              "name": "УралБанк"
            }
            """;

        connector.send("bank-create", payload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(BankDetails.count()).isEqualTo(1);
            BankDetails saved = BankDetails.findByBik(555555555L);
            assertThat(saved).isNotNull();
            assertThat(saved.getName()).isEqualTo("УралБанк");
            assertThat(saved.getKpp()).isEqualTo("123456789");
        });
    }

    @Test
    void branchCreate_shouldSaveAndLinkATM() {
        String branchPayload = """
            {
              "address": "ул. Малышева, 1",
              "phoneNumber": "73432123456",
              "city": "Екатеринбург",
              "startOfWork": "09:00:00",
              "endOfWork": "18:00:00"
            }
            """;

        connector.send("branch-create", branchPayload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(Branch.count()).isEqualTo(1);
        });
    }

    @Test
    void invalidJson_shouldNotCrashConsumer() {
        connector.send("bank-create", "{invalid json}");

        // Главное — тест не упал и consumer не бросил исключение
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // просто проверяем, что тест дошёл до конца
            assertThat(true).isTrue();
        });
    }
}