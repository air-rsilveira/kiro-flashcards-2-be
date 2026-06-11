package com.flashcards.service;

import com.flashcards.entity.ReviewResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpacedRepetitionEngineTest {

    private SpacedRepetitionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SpacedRepetitionEngine();
    }

    @Test
    void calculateNextReview_again_addsOneMinute() {
        Instant reviewedAt = Instant.parse("2024-01-15T10:00:00Z");
        Instant expected = reviewedAt.plus(1, ChronoUnit.MINUTES);

        Instant result = engine.calculateNextReview(reviewedAt, ReviewResult.AGAIN);

        assertEquals(expected, result);
    }

    @Test
    void calculateNextReview_hard_addsTenMinutes() {
        Instant reviewedAt = Instant.parse("2024-01-15T10:00:00Z");
        Instant expected = reviewedAt.plus(10, ChronoUnit.MINUTES);

        Instant result = engine.calculateNextReview(reviewedAt, ReviewResult.HARD);

        assertEquals(expected, result);
    }

    @Test
    void calculateNextReview_good_addsOneDay() {
        Instant reviewedAt = Instant.parse("2024-01-15T10:00:00Z");
        Instant expected = reviewedAt.plus(1, ChronoUnit.DAYS);

        Instant result = engine.calculateNextReview(reviewedAt, ReviewResult.GOOD);

        assertEquals(expected, result);
    }

    @Test
    void calculateNextReview_easy_addsFourDays() {
        Instant reviewedAt = Instant.parse("2024-01-15T10:00:00Z");
        Instant expected = reviewedAt.plus(4, ChronoUnit.DAYS);

        Instant result = engine.calculateNextReview(reviewedAt, ReviewResult.EASY);

        assertEquals(expected, result);
    }
}
