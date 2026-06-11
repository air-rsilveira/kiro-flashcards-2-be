package com.flashcards.service;

import com.flashcards.entity.ReviewResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SpacedRepetitionEngine {

    public Instant calculateNextReview(Instant reviewedAt, ReviewResult result) {
        return switch (result) {
            case AGAIN -> reviewedAt.plus(1, ChronoUnit.MINUTES);
            case HARD -> reviewedAt.plus(10, ChronoUnit.MINUTES);
            case GOOD -> reviewedAt.plus(1, ChronoUnit.DAYS);
            case EASY -> reviewedAt.plus(4, ChronoUnit.DAYS);
        };
    }
}
