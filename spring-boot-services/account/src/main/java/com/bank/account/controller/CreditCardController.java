package com.bank.account.controller;

import com.bank.account.dto.CreateCreditCardRequest;
import com.bank.account.dto.CreditCardDto;
import com.bank.account.dto.UpdateCardLimitRequest;
import com.bank.account.service.CreditCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for credit card operations.
 */
 /**
 * <p>Base URL: /api/account/api/cards  (с учётом context-path /api/account)
 */
@RestController
@RequestMapping("/api/cards")
@Tag(name = "Credit Card", description = "Credit card management API")
@RequiredArgsConstructor
@Slf4j
public class CreditCardController {

    private final CreditCardService creditCardService;

    @Operation(summary = "Create new credit card")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Card created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PostMapping
    public ResponseEntity<CreditCardDto> createCard(
            @Valid @RequestBody CreateCreditCardRequest request) {
        log.info("REST: Create credit card for account: {}", request.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.create(request));
    }

    @Operation(summary = "Get credit card by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card found"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CreditCardDto> getCardById(
            @Parameter(description = "Card ID") @PathVariable Long id) {
        log.info("REST: Get credit card by ID: {}", id);
        return ResponseEntity.ok(creditCardService.getById(id));
    }

    @Operation(summary = "Get credit card by card number")
    @GetMapping("/number/{cardNumber}")
    public ResponseEntity<CreditCardDto> getCardByNumber(
            @Parameter(description = "16-digit card number") @PathVariable String cardNumber) {
        log.info("REST: Get credit card by number");
        return ResponseEntity.ok(creditCardService.getByCardNumber(cardNumber));
    }

    @Operation(summary = "Get all cards for an account")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<CreditCardDto>> getCardsByAccount(
            @Parameter(description = "Account ID") @PathVariable Long accountId) {
        log.info("REST: Get cards for account: {}", accountId);
        return ResponseEntity.ok(creditCardService.getByAccountId(accountId));
    }

    @Operation(summary = "Block credit card")
    @PatchMapping("/{id}/block")
    public ResponseEntity<CreditCardDto> blockCard(
            @Parameter(description = "Card ID") @PathVariable Long id) {
        log.info("REST: Block credit card: {}", id);
        return ResponseEntity.ok(creditCardService.block(id));
    }

    @Operation(summary = "Unblock credit card")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<CreditCardDto> unblockCard(
            @Parameter(description = "Card ID") @PathVariable Long id) {
        log.info("REST: Unblock credit card: {}", id);
        return ResponseEntity.ok(creditCardService.unblock(id));
    }

    @Operation(summary = "Update card limits")
    @PutMapping("/{id}/limit")
    public ResponseEntity<CreditCardDto> updateLimit(
            @Parameter(description = "Card ID") @PathVariable Long id,
            @Valid @RequestBody UpdateCardLimitRequest request) {
        log.info("REST: Update limits for card: {}", id);
        return ResponseEntity.ok(creditCardService.updateLimit(id, request));
    }

    @Operation(summary = "Delete credit card")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(
            @Parameter(description = "Card ID") @PathVariable Long id) {
        log.info("REST: Delete credit card: {}", id);
        creditCardService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
