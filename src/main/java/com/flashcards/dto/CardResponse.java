package com.flashcards.dto;

import java.time.Instant;
import java.util.UUID;

public record CardResponse(
        UUID id,
        UUID deckId,
        String front,
        String back,
        Instant createdAt,
        Instant updatedAt) {
}
