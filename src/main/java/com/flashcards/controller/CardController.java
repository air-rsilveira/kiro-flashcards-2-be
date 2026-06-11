package com.flashcards.controller;

import com.flashcards.dto.CardResponse;
import com.flashcards.dto.CreateCardRequest;
import com.flashcards.dto.ErrorResponse;
import com.flashcards.dto.UpdateCardRequest;
import com.flashcards.service.CardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping("/api/decks/{deckId}/cards")
    public ResponseEntity<CardResponse> createCard(
            @PathVariable UUID deckId,
            @Valid @RequestBody CreateCardRequest request) {
        CardResponse response = cardService.createCard(deckId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/decks/{deckId}/cards")
    public ResponseEntity<?> listCards(
            @PathVariable UUID deckId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            ErrorResponse error = new ErrorResponse(
                    Instant.now(),
                    400,
                    "Validation Error",
                    "Invalid pagination parameters: page must be >= 0 and size must be between 1 and 100"
            );
            return ResponseEntity.badRequest().body(error);
        }
        Page<CardResponse> cards = cardService.listCards(deckId, q, PageRequest.of(page, size));
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/api/cards/{id}")
    public ResponseEntity<CardResponse> getCardById(@PathVariable UUID id) {
        CardResponse response = cardService.getCardById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/cards/{id}")
    public ResponseEntity<CardResponse> updateCard(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCardRequest request) {
        CardResponse response = cardService.updateCard(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable UUID id) {
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }
}
