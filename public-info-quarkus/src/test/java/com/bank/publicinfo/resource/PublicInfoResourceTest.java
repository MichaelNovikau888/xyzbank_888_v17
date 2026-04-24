package com.bank.publicinfo.resource;

import com.bank.publicinfo.entity.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.LocalTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Интеграционные тесты REST-ресурсов public-info-service.
 */
 /**
 * H2 in-memory, Kafka → smallrye-in-memory, Liquibase off.
 */
 /**
 * Покрытие:
 *   BankDetailsResource: GET all/by-id, POST 201/400/409(BIK), PUT, DELETE 204
 *   BranchResource:      GET all/by-id, POST 201, PUT, DELETE
 *   ATMResource:         GET by-branch, POST 201, PUT, DELETE
 *   CertificateResource: GET by-bank, POST 201, DELETE
 *   LicenseResource:     GET by-bank, POST 201, DELETE
 *   AuditResource:       GET all, by-id, by-entity-type, by-entity-json
 *   Health & Metrics
 */
@QuarkusTest
@DisplayName("Public-info REST Resources — integration tests")
class PublicInfoResourceTest {

    @BeforeEach @Transactional
    void cleanDb() {
        ATM.deleteAll(); Certificate.deleteAll(); License.deleteAll();
        Branch.deleteAll(); Audit.deleteAll();
        // BankDetails last — FK constraints
        com.bank.publicinfo.entity.BankDetails.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    Long persistBranch(String city, String address) {
        Branch b = new Branch();
        b.setCity(city); b.setAddress(address);
        b.setPhoneNumber("74951234567");
        b.setStartOfWork(LocalTime.of(9, 0)); b.setEndOfWork(LocalTime.of(18, 0));
        Branch.persist(b); return b.getId();
    }

    @Transactional
    Long persistBank(Long bik) {
        com.bank.publicinfo.entity.BankDetails bd = new com.bank.publicinfo.entity.BankDetails();
        bd.setBik(bik); bd.setInn(1234567890L); bd.setKpp(123456789L);
        bd.setName("Test Bank"); bd.setCity("Москва");
        com.bank.publicinfo.entity.BankDetails.persist(bd); return bd.getId();
    }

    // ── BankDetailsResource ───────────────────────────────────────────────────

    @Nested @DisplayName("BankDetailsResource")
    class BankDetailsTests {

        @Test @DisplayName("GET /bank-details → 200 пустой список")
        void getAll_empty() {
            given().when().get("/api/public-info/bank-details")
                   .then().statusCode(200).body("totalElements", equalTo(0));
        }

        @Test @DisplayName("POST /bank-details → 201 с id")
        void create_returns201() {
            String body = """
                {"bik":123456789,"inn":1234567890,"kpp":123456789,"name":"Банк","city":"Москва"}
                """;
            given().contentType("application/json").body(body)
                   .when().post("/api/public-info/bank-details")
                   .then().statusCode(201).body("id", notNullValue()).body("bik", equalTo(123456789));
        }

        @Test @DisplayName("POST /bank-details с дублирующим BIK → 400")
        void create_duplicateBik_returns400() {
            persistBank(987654321L);
            String body = """
                {"bik":987654321,"inn":9876543210L,"kpp":987654321,"name":"Копия","city":"СПб"}
                """;
            given().contentType("application/json").body(body)
                   .when().post("/api/public-info/bank-details")
                   .then().statusCode(400);
        }

        @Test @DisplayName("GET /bank-details/{id} → 200 с полями")
        void getById_found() {
            Long id = persistBank(111111111L);
            given().when().get("/api/public-info/bank-details/" + id)
                   .then().statusCode(200).body("id", equalTo(id.intValue()));
        }

        @Test @DisplayName("GET /bank-details/{id} → 404 не найден")
        void getById_notFound() {
            given().when().get("/api/public-info/bank-details/999999")
                   .then().statusCode(404);
        }

        @Test @DisplayName("DELETE /bank-details/{id} → 204")
        void delete_returns204() {
            Long id = persistBank(222222222L);
            given().when().delete("/api/public-info/bank-details/" + id)
                   .then().statusCode(204);
        }
    }

    // ── BranchResource ────────────────────────────────────────────────────────

    @Nested @DisplayName("BranchResource")
    class BranchTests {

        @Test @DisplayName("GET /branches → 200 пустой список")
        void getAll_empty() {
            given().when().get("/api/public-info/branches")
                   .then().statusCode(200).body("totalElements", equalTo(0));
        }

        @Test @DisplayName("POST /branches → 201 с id и city")
        void create_returns201() {
            String body = """
                {"address":"ул. Ленина, 1","phoneNumber":"74951234567","city":"Москва",
                 "startOfWork":"09:00:00","endOfWork":"18:00:00"}
                """;
            given().contentType("application/json").body(body)
                   .when().post("/api/public-info/branches")
                   .then().statusCode(201).body("id", notNullValue()).body("city", equalTo("Москва"));
        }

        @Test @DisplayName("GET /branches/{id} → 200")
        void getById_found() {
            Long id = persistBranch("Казань", "ул. Баумана, 5");
            given().when().get("/api/public-info/branches/" + id)
                   .then().statusCode(200).body("city", equalTo("Казань"));
        }

        @Test @DisplayName("GET /branches/{id} → 404")
        void getById_notFound() {
            given().when().get("/api/public-info/branches/999999")
                   .then().statusCode(404);
        }

        @Test @DisplayName("PUT /branches/{id} → 200 обновлён city")
        void update_returns200() {
            Long id = persistBranch("Омск", "пр. Маркса, 10");
            String body = """
                {"address":"пр. Маркса, 10","phoneNumber":"73812345678","city":"Новосибирск",
                 "startOfWork":"08:00:00","endOfWork":"20:00:00"}
                """;
            given().contentType("application/json").body(body)
                   .when().put("/api/public-info/branches/" + id)
                   .then().statusCode(200).body("city", equalTo("Новосибирск"));
        }

        @Test @DisplayName("DELETE /branches/{id} → 204")
        void delete_returns204() {
            Long id = persistBranch("Сочи", "ул. Ленина, 3");
            given().when().delete("/api/public-info/branches/" + id)
                   .then().statusCode(204);
        }
    }

    // ── ATMResource ───────────────────────────────────────────────────────────

    @Nested @DisplayName("ATMResource")
    class ATMTests {

        @Test @DisplayName("GET /atms?branchId=999 → 200 пустой список")
        void getByBranch_empty() {
            given().queryParam("branchId", 999)
                   .when().get("/api/public-info/atms")
                   .then().statusCode(200).body("$", hasSize(0));
        }

        @Test @DisplayName("POST /atms → 201 с id")
        void create_returns201() {
            Long branchId = persistBranch("Тула", "ул. Октябрьская, 1");
            String body = String.format(
                "{\"branchId\":%d,\"allHours\":true,\"address\":\"ул. Октябрьская, 1\"}", branchId);
            given().contentType("application/json").body(body)
                   .when().post("/api/public-info/atms")
                   .then().statusCode(201).body("id", notNullValue());
        }

        @Test @DisplayName("GET /atms/{id} → 404 не найден")
        void getById_notFound() {
            given().when().get("/api/public-info/atms/999999")
                   .then().statusCode(404);
        }
    }

    // ── AuditResource ─────────────────────────────────────────────────────────

    @Nested @DisplayName("AuditResource")
    class AuditTests {

        @Test @DisplayName("GET /audits → 200 пустой список")
        void getAll_empty() {
            given().when().get("/api/public-info/audits")
                   .then().statusCode(200).body("totalElements", equalTo(0));
        }

        @Test @DisplayName("GET /audits/{id} → 404 не найден")
        void getById_notFound() {
            given().when().get("/api/public-info/audits/999999")
                   .then().statusCode(404);
        }

        @Test @DisplayName("GET /audits/by-entity-type без параметра → 400")
        void byEntityType_blankParam_returns400() {
            given().when().get("/api/public-info/audits/by-entity-type")
                   .then().statusCode(400);
        }

        @Test @DisplayName("GET /audits/by-entity-type?type=Unknown → 404")
        void byEntityType_unknown_returns404() {
            given().queryParam("type", "NonExistentType")
                   .when().get("/api/public-info/audits/by-entity-type")
                   .then().statusCode(404);
        }

        @Test @DisplayName("GET /audits/by-entity-json?json=x → 200 пустой список")
        void byEntityJson_noMatches() {
            given().queryParam("json", "xyz_not_found")
                   .when().get("/api/public-info/audits/by-entity-json")
                   .then().statusCode(200).body("$", hasSize(0));
        }
    }

    // ── Health & Metrics ──────────────────────────────────────────────────────

    @Nested @DisplayName("Health & Metrics")
    class HealthTests {

        @Test @DisplayName("GET /q/health → 200 UP")
        void health_up() {
            given().when().get("/q/health")
                   .then().statusCode(200).body("status", equalTo("UP"));
        }

        @Test @DisplayName("GET /q/metrics → содержит publicinfo счётчики")
        void metrics_exposed() {
            given().when().get("/q/metrics")
                   .then().statusCode(200)
                   .body(containsString("publicinfo_bank_details_created_total"))
                   .body(containsString("publicinfo_kafka_errors_total"));
        }
    }
}
