package com.flashcards.dto;

import com.flashcards.entity.ReviewResult;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID cardId,
        ReviewResult result,
        Instant reviewedAt,
        Instant nextReviewAt) {
}
