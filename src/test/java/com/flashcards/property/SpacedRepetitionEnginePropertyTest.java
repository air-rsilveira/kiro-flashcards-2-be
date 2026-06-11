package com.flashcards.property;

import com.flashcards.entity.ReviewResult;
import com.flashcards.service.SpacedRepetitionEngine;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Property-based tests for SpacedRepetitionEngine.
 * Validates: Requirements 12.4
 */
class SpacedRepetitionEnginePropertyTest {

    private final SpacedRepetitionEngine engine = new SpacedRepetitionEngine();

    /**
     * Property 1: Spaced Repetition Interval Calculation
     *
     * For any valid Instant timestamp and any ReviewResult value (EASY, GOOD, HARD, AGAIN),
     * the SpacedRepetitionEngine.calculateNextReview() SHALL return the timestamp plus the
     * exact interval defined: AGAIN → +1 minute, HARD → +10 minutes, GOOD → +1 day, EASY → +4 days.
     *
     * Validates: Requirements 12.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 1: Spaced Repetition Interval Calculation")
    void intervalCalculationIsCorrect(@ForAll("timestamps") Instant reviewedAt, @ForAll ReviewResult result) {
        Instant actual = engine.calculateNextReview(reviewedAt, result);

        Instant expected = switch (result) {
            case AGAIN -> reviewedAt.plus(1, ChronoUnit.MINUTES);
            case HARD -> reviewedAt.plus(10, ChronoUnit.MINUTES);
            case GOOD -> reviewedAt.plus(1, ChronoUnit.DAYS);
            case EASY -> reviewedAt.plus(4, ChronoUnit.DAYS);
        };

        assert actual.equals(expected) :
                String.format("For result=%s, reviewedAt=%s: expected %s but got %s",
                        result, reviewedAt, expected, actual);
    }

    @Provide
    Arbitrary<Instant> timestamps() {
        return Arbitraries.longs()
                .between(0, Instant.now().getEpochSecond())
                .map(Instant::ofEpochSecond);
    }
}
