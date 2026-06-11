package com.flashcards.controller;

import com.flashcards.dto.CardResponse;
import com.flashcards.service.CardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/decks/{deckId}/study")
public class StudyController {

    private final CardService cardService;

    public StudyController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public ResponseEntity<List<CardResponse>> getCardsForStudy(@PathVariable UUID deckId) {
        List<CardResponse> cards = cardService.getCardsForStudy(deckId);
        return ResponseEntity.ok(cards);
    }
}
