package com.flashcards.dto;

import java.time.Instant;
import java.util.UUID;

public record DeckResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
