package com.flashcards.controller;

import com.flashcards.dto.CreateDeckRequest;
import com.flashcards.dto.DeckResponse;
import com.flashcards.dto.ErrorResponse;
import com.flashcards.dto.UpdateDeckRequest;
import com.flashcards.service.DeckService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @PostMapping
    public ResponseEntity<DeckResponse> createDeck(@Valid @RequestBody CreateDeckRequest request) {
        DeckResponse response = deckService.createDeck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<?> listDecks(
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

        Page<DeckResponse> decks = deckService.listDecks(PageRequest.of(page, size));
        return ResponseEntity.ok(decks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeckResponse> getDeckById(@PathVariable UUID id) {
        DeckResponse response = deckService.getDeckById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeckResponse> updateDeck(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeckRequest request) {
        DeckResponse response = deckService.updateDeck(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeck(@PathVariable UUID id) {
        deckService.deleteDeck(id);
        return ResponseEntity.noContent().build();
    }
}
