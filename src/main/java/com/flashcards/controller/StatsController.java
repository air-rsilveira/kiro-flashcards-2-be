package com.flashcards.controller;

import com.flashcards.dto.StatsResponse;
import com.flashcards.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/decks/{deckId}/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<StatsResponse> getDeckStats(@PathVariable UUID deckId) {
        StatsResponse response = statsService.getDeckStats(deckId);
        return ResponseEntity.ok(response);
    }
}
