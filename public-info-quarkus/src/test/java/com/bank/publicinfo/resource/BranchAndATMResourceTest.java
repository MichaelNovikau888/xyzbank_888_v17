package com.bank.publicinfo.resource;

import com.bank.publicinfo.entity.ATM;
import com.bank.publicinfo.entity.Branch;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.LocalTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты BranchResource и ATMResource.
 * H2 in-memory, Kafka → smallrye-in-memory.
 */
@QuarkusTest
class BranchAndATMResourceTest {

    @BeforeEach
    @Transactional
    void cleanDb() {
        ATM.deleteAll();
        Branch.deleteAll();
    }

    @Transactional
    Branch persistBranch(String address, long phone, String city) {
        Branch b = new Branch();
        b.setAddress(address); b.setPhoneNumber(String.valueOf(phone)); b.setCity(city);
        b.setStartOfWork(LocalTime.of(9, 0)); b.setEndOfWork(LocalTime.of(18, 0));
        b.persistAndFlush();
        return b;
    }

    // ── BranchResource ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /branches → 200 пустой список")
    void branches_getAll_empty() {
        given().when().get("/api/public-info/branches")
               .then().statusCode(200).body("totalElements", equalTo(0));
    }

    @Test
    @DisplayName("POST /branches → 201")
    void branches_create_returns201() {
        String body = """
            {
              "address": "ул. Ленина, 1",
              "phoneNumber": 74951234567,
              "city": "Москва",
              "startOfWork": "09:00:00",
              "endOfWork": "18:00:00"
            }
            """;
        given().contentType("application/json").body(body)
               .when().post("/api/public-info/branches")
               .then().statusCode(201)
               .body("id",   notNullValue())
               .body("city", equalTo("Москва"));
    }

    @Test
    @DisplayName("GET /branches/{id} → 404 если не существует")
    void branches_getById_notFound() {
        given().when().get("/api/public-info/branches/9999")
               .then().statusCode(404)
               .body("errorCode", equalTo("ENTITY_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("DELETE /branches/{id} → 204")
    void branches_delete_returns204() {
        Branch b = persistBranch("ул. Тест, 1", 74951000001L, "Москва");
        given().when().delete("/api/public-info/branches/" + b.getId())
               .then().statusCode(204);
    }

    @Test
    @Transactional
    @DisplayName("GET /branches/{id} → 200 с нужными данными")
    void branches_getById_found() {
        Branch b = persistBranch("пр. Победы, 7", 74951000002L, "Уфа");
        given().when().get("/api/public-info/branches/" + b.getId())
               .then().statusCode(200)
               .body("city",    equalTo("Уфа"))
               .body("address", equalTo("пр. Победы, 7"));
    }

    // ── ATMResource ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /atms?branchId=9999 → пустой список")
    void atms_getByUnknownBranch_returnsEmpty() {
        given().queryParam("branchId", 9999)
               .when().get("/api/public-info/atms")
               .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @DisplayName("GET /atms/9999 → 404")
    void atms_getById_notFound() {
        given().when().get("/api/public-info/atms/9999")
               .then().statusCode(404);
    }

    @Test
    @Transactional
    @DisplayName("POST /atms → 201, GET /atms?branchId → возвращает банкомат")
    void atms_createAndGetByBranch() {
        Branch branch = persistBranch("ул. Мира, 5", 74951000003L, "Казань");

        String atmBody = String.format(
            "{\"address\":\"ул. Мира, 5, лобби\",\"allHours\":true,\"branchId\":%d}",
            branch.getId()
        );
        Integer atmId = given().contentType("application/json").body(atmBody)
               .when().post("/api/public-info/atms")
               .then().statusCode(201)
               .body("allHours", equalTo(true))
               .extract().path("id");

        given().queryParam("branchId", branch.getId())
               .when().get("/api/public-info/atms")
               .then().statusCode(200)
               .body("size()", equalTo(1))
               .body("[0].id", equalTo(atmId));
    }

    @Test
    @Transactional
    @DisplayName("DELETE /atms/{id} → 204, GET → пустой список")
    void atms_deleteAndVerify() {
        Branch branch = persistBranch("ул. Садовая, 3", 74951000004L, "Сочи");
        ATM atm = new ATM();
        atm.setAddress("ул. Садовая, 3"); atm.setAllHours(false);
        atm.setBranch(branch);
        atm.persistAndFlush();

        given().when().delete("/api/public-info/atms/" + atm.getId())
               .then().statusCode(204);

        given().queryParam("branchId", branch.getId())
               .when().get("/api/public-info/atms")
               .then().statusCode(200).body("size()", equalTo(0));
    }
}
