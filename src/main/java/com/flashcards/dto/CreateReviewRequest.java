package com.flashcards.dto;

import com.flashcards.entity.ReviewResult;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
        @NotNull ReviewResult result) {
}
