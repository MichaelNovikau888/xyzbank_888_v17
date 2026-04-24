package com.bank.account.controller;

import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.entity.Account;
import com.bank.account.entity.CreditCard;
import com.bank.account.enums.CardStatus;
import com.bank.account.enums.CardType;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CreditCardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreditCardControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CreditCardRepository creditCardRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        creditCardRepository.deleteAll();
        accountRepository.deleteAll();

        testAccount = new Account();
        testAccount.setPassportId(123L);
        testAccount.setAccountNumber("4081781009991000"); // 16-значный номер — Long.MAX_VALUE безопасен
        testAccount.setBankDetailsId(1L);
        testAccount.setMoney(BigDecimal.valueOf(10000));
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    @DisplayName("POST /api/cards — должен создать карту")
    void shouldCreateCreditCardViaApi() throws Exception {
        CreateCreditCardRequest request = buildRequest();

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.maskedCardNumber").exists())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId()))
                .andExpect(jsonPath("$.cardholderName").value("John Doe"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/cards/account/{id} — должен вернуть список карт")
    void shouldGetCardsByAccountId() throws Exception {
        createTestCard();
        createTestCard();

        mockMvc.perform(get("/api/cards/account/" + testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PATCH /api/cards/{id}/block — должен заблокировать карту")
    void shouldBlockCard() throws Exception {
        CreditCard card = createTestCard();

        mockMvc.perform(patch("/api/cards/" + card.getId() + "/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    @DisplayName("GET /api/cards/{id} — должен вернуть 404 для несуществующей карты")
    void shouldReturn404ForMissingCard() throws Exception {
        mockMvc.perform(get("/api/cards/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/cards/{id} — должен удалить карту")
    void shouldDeleteCard() throws Exception {
        CreditCard card = createTestCard();

        mockMvc.perform(delete("/api/cards/" + card.getId()))
                .andExpect(status().isNoContent());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CreateCreditCardRequest buildRequest() {
        CreateCreditCardRequest request = new CreateCreditCardRequest();
        request.setAccountId(testAccount.getId());
        request.setCardholderName("John Doe");
        request.setCardType(CardType.VISA);
        request.setExpiryDate(LocalDate.now().plusYears(3));
        request.setCvv("123");
        return request;
    }

    private CreditCard createTestCard() {
        CreditCard card = new CreditCard();
        card.setCardNumber(generateCardNumber());
        card.setAccountId(testAccount.getId());
        card.setCardholderName("Test User");
        card.setCardType(CardType.VISA);
        card.setExpiryDate(LocalDate.now().plusYears(3));
        card.setCvvHash("hashed_cvv");
        card.setStatus(CardStatus.ACTIVE);
        return creditCardRepository.save(card);
    }

    private String generateCardNumber() {
        // Простой уникальный номер для тестов (не Luhn-валидный, но уникальный)
        long suffix = System.nanoTime() % 1_000_000_000_000L;
        return String.format("4%015d", Math.abs(suffix)).substring(0, 16);
    }
}
