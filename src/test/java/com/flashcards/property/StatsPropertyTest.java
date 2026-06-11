package com.flashcards.property;

import com.flashcards.dto.StatsResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based tests for Statistics endpoint.
 * Validates: Requirements 13.1, 13.3, 13.4
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatsPropertyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @AfterProperty
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    /**
     * Property 14: Stats Computation Correctness
     *
     * For any Deck with a known set of Cards and Reviews, the stats endpoint SHALL return:
     * totalCards equal to the Card count, cardsStudied equal to the count of Cards with at least one Review,
     * cardsDue equal to the count of Cards either never reviewed or with nextReviewAt <= now,
     * totalReviews equal to the total Review count, and resultDistribution matching the actual count
     * per ReviewResult value.
     *
     * Validates: Requirements 13.1, 13.3
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 14: Stats Computation Correctness")
    void statsComputationIsCorrect(@ForAll("cardCounts") int cardCount,
                                   @ForAll("reviewConfigurations") ReviewConfiguration config) {
        // Create deck
        Deck deck = createDeck("Stats Test Deck");
        Instant now = Instant.now();

        // Create cards
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            cards.add(createCard(deck.getId(), "Front " + i, "Back " + i));
        }

        if (cards.isEmpty()) {
            // Empty deck should return all zeros
            StatsResponse stats = getStats(deck.getId());
            assert stats.totalCards() == 0 : "Expected totalCards=0 for empty deck, got " + stats.totalCards();
            assert stats.cardsStudied() == 0 : "Expected cardsStudied=0 for empty deck, got " + stats.cardsStudied();
            assert stats.cardsDue() == 0 : "Expected cardsDue=0 for empty deck, got " + stats.cardsDue();
            assert stats.totalReviews() == 0 : "Expected totalReviews=0 for empty deck, got " + stats.totalReviews();
            assert stats.studyStreak() == 0 : "Expected studyStreak=0 for empty deck, got " + stats.studyStreak();
            assert stats.resultDistribution().easy() == 0 : "Expected easy=0";
            assert stats.resultDistribution().good() == 0 : "Expected good=0";
            assert stats.resultDistribution().hard() == 0 : "Expected hard=0";
            assert stats.resultDistribution().again() == 0 : "Expected again=0";
            return;
        }

        // Track expected values
        Set<UUID> studiedCardIds = new HashSet<>();
        int totalReviewCount = 0;
        long easyCount = 0, goodCount = 0, hardCount = 0, againCount = 0;

        // Map to track latest review per card for cardsDue calculation
        Map<UUID, Instant> latestNextReviewAt = new HashMap<>();

        // Apply review configuration to cards
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int reviewPattern = config.getReviewPattern(i, cards.size());

            if (reviewPattern == 0) {
                // No reviews for this card (never reviewed)
                continue;
            }

            studiedCardIds.add(card.getId());

            // Create reviews based on pattern
            List<ReviewResult> results = config.getResults(i, reviewPattern);
            for (int r = 0; r < results.size(); r++) {
                ReviewResult result = results.get(r);
                totalReviewCount++;

                switch (result) {
                    case EASY -> easyCount++;
                    case GOOD -> goodCount++;
                    case HARD -> hardCount++;
                    case AGAIN -> againCount++;
                }

                // Create the review with timestamps
                Instant reviewedAt = now.minus((results.size() - r) * 2L, ChronoUnit.HOURS);
                Instant nextReviewAt;
                if (reviewPattern == 1) {
                    // Due card: nextReviewAt in the past
                    nextReviewAt = now.minus(10, ChronoUnit.MINUTES);
                } else if (reviewPattern == 2) {
                    // Not due card: nextReviewAt in the future
                    nextReviewAt = now.plus(2, ChronoUnit.DAYS);
                } else {
                    // Mixed: last review determines due status
                    if (r == results.size() - 1) {
                        nextReviewAt = now.minus(5, ChronoUnit.MINUTES); // due
                    } else {
                        nextReviewAt = now.minus((results.size() - r) * 30L, ChronoUnit.MINUTES);
                    }
                }

                createReview(card.getId(), result, reviewedAt, nextReviewAt);

                // Track latest nextReviewAt for this card
                if (!latestNextReviewAt.containsKey(card.getId()) ||
                        reviewedAt.isAfter(now.minus((results.size() - (r - 1)) * 2L, ChronoUnit.HOURS))) {
                    latestNextReviewAt.put(card.getId(), nextReviewAt);
                }
            }
        }

        // Calculate expected cardsDue: cards never reviewed + cards with latest nextReviewAt <= now
        long expectedCardsDue = 0;
        for (Card card : cards) {
            Instant nextReview = latestNextReviewAt.get(card.getId());
            if (nextReview == null) {
                // Never reviewed -> due
                expectedCardsDue++;
            } else if (!nextReview.isAfter(now)) {
                // nextReviewAt <= now -> due
                expectedCardsDue++;
            }
        }

        // Call stats endpoint
        StatsResponse stats = getStats(deck.getId());

        // Verify all fields
        assert stats.totalCards() == cardCount :
                String.format("Expected totalCards=%d, got %d", cardCount, stats.totalCards());
        assert stats.cardsStudied() == studiedCardIds.size() :
                String.format("Expected cardsStudied=%d, got %d", studiedCardIds.size(), stats.cardsStudied());
        assert stats.cardsDue() == (int) expectedCardsDue :
                String.format("Expected cardsDue=%d, got %d", expectedCardsDue, stats.cardsDue());
        assert stats.totalReviews() == totalReviewCount :
                String.format("Expected totalReviews=%d, got %d", totalReviewCount, stats.totalReviews());
        assert stats.resultDistribution().easy() == easyCount :
                String.format("Expected easy=%d, got %d", easyCount, stats.resultDistribution().easy());
        assert stats.resultDistribution().good() == goodCount :
                String.format("Expected good=%d, got %d", goodCount, stats.resultDistribution().good());
        assert stats.resultDistribution().hard() == hardCount :
                String.format("Expected hard=%d, got %d", hardCount, stats.resultDistribution().hard());
        assert stats.resultDistribution().again() == againCount :
                String.format("Expected again=%d, got %d", againCount, stats.resultDistribution().again());
    }

    /**
     * Property 15: Study Streak Calculation
     *
     * For any sequence of Review timestamps in a Deck, the studyStreak SHALL equal the number
     * of consecutive days (counting backwards from today) that have at least one Review.
     * If today has no Reviews, studyStreak SHALL be 0.
     *
     * Validates: Requirements 13.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 15: Study Streak Calculation")
    void studyStreakCalculationIsCorrect(@ForAll("streakScenarios") StreakScenario scenario) {
        Deck deck = createDeck("Streak Test Deck");
        Card card = createCard(deck.getId(), "Streak Front", "Streak Back");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant now = Instant.now();

        // Create reviews on the specified days
        for (int dayOffset : scenario.daysWithReviews()) {
            // dayOffset = 0 means today, 1 means yesterday, etc.
            LocalDate reviewDate = today.minusDays(dayOffset);
            Instant reviewedAt = reviewDate.atStartOfDay(ZoneOffset.UTC).toInstant().plus(12, ChronoUnit.HOURS);
            Instant nextReviewAt = reviewedAt.plus(1, ChronoUnit.DAYS);
            createReview(card.getId(), ReviewResult.GOOD, reviewedAt, nextReviewAt);
        }

        // Call stats endpoint
        StatsResponse stats = getStats(deck.getId());

        assert stats.studyStreak() == scenario.expectedStreak() :
                String.format("Expected studyStreak=%d, got %d. Days with reviews: %s",
                        scenario.expectedStreak(), stats.studyStreak(),
                        Arrays.toString(scenario.daysWithReviews()));
    }

    // --- Arbitraries / Providers ---

    @Provide
    Arbitrary<Integer> cardCounts() {
        return Arbitraries.integers().between(0, 8);
    }

    @Provide
    Arbitrary<ReviewConfiguration> reviewConfigurations() {
        return Arbitraries.of(
                // 0 = no reviews, 1 = due reviews, 2 = future reviews, 3 = multiple reviews (due)
                new ReviewConfiguration(new int[]{0, 1, 2}),        // mix: none, due, future
                new ReviewConfiguration(new int[]{0, 0, 0}),        // all never reviewed
                new ReviewConfiguration(new int[]{1, 1, 1}),        // all with due reviews
                new ReviewConfiguration(new int[]{2, 2, 2}),        // all with future reviews
                new ReviewConfiguration(new int[]{1, 0, 2, 1}),     // mixed pattern
                new ReviewConfiguration(new int[]{3, 1, 0, 2}),     // with multiple reviews
                new ReviewConfiguration(new int[]{1, 2, 1, 2, 0})   // alternating
        );
    }

    @Provide
    Arbitrary<StreakScenario> streakScenarios() {
        return Arbitraries.of(
                // No reviews today -> streak = 0
                new StreakScenario(new int[]{1}, 0),
                // Reviews only yesterday -> streak = 0
                new StreakScenario(new int[]{1, 2, 3}, 0),
                // Review today only -> streak = 1
                new StreakScenario(new int[]{0}, 1),
                // Reviews today and yesterday -> streak = 2
                new StreakScenario(new int[]{0, 1}, 2),
                // Reviews today, yesterday, day before -> streak = 3
                new StreakScenario(new int[]{0, 1, 2}, 3),
                // Reviews today, yesterday, day before, 3 days ago -> streak = 4
                new StreakScenario(new int[]{0, 1, 2, 3}, 4),
                // Gap in the middle: today + 2 days ago (no yesterday) -> streak = 1
                new StreakScenario(new int[]{0, 2}, 1),
                // Gap: today, yesterday, then skip, then day 3 -> streak = 2
                new StreakScenario(new int[]{0, 1, 3}, 2),
                // Long streak: 5 consecutive days from today
                new StreakScenario(new int[]{0, 1, 2, 3, 4}, 5),
                // No reviews at all -> streak = 0 (empty deck scenario handled differently)
                new StreakScenario(new int[]{}, 0),
                // Today with gap at day 1 -> streak = 1
                new StreakScenario(new int[]{0, 2, 3, 4}, 1),
                // Today + yesterday + gap at day 2 -> streak = 2
                new StreakScenario(new int[]{0, 1, 3, 4, 5}, 2)
        );
    }

    // --- Helper methods ---

    private Deck createDeck(String name) {
        Deck deck = new Deck();
        deck.setName(name);
        deck.setDescription("Test description");
        return deckRepository.saveAndFlush(deck);
    }

    private Card createCard(UUID deckId, String front, String back) {
        Card card = new Card();
        card.setDeckId(deckId);
        card.setFront(front);
        card.setBack(back);
        return cardRepository.saveAndFlush(card);
    }

    private void createReview(UUID cardId, ReviewResult result, Instant reviewedAt, Instant nextReviewAt) {
        Review review = new Review();
        review.setCardId(cardId);
        review.setResult(result);
        review.setReviewedAt(reviewedAt);
        review.setNextReviewAt(nextReviewAt);
        reviewRepository.saveAndFlush(review);
    }

    private StatsResponse getStats(UUID deckId) {
        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/" + deckId + "/stats",
                StatsResponse.class
        );
        assert response.getStatusCode().is2xxSuccessful() :
                "Expected 200 OK but got " + response.getStatusCode();
        return response.getBody();
    }

    // --- Inner classes ---

    record ReviewConfiguration(int[] patterns) {
        /**
         * Returns the review pattern for a given card index.
         * 0 = no reviews (never reviewed)
         * 1 = has reviews, card is due (nextReviewAt in the past)
         * 2 = has reviews, card is not due (nextReviewAt in the future)
         * 3 = has multiple reviews, card is due
         */
        int getReviewPattern(int index, int totalCards) {
            return patterns[index % patterns.length];
        }

        /**
         * Returns the list of review results for a card based on pattern.
         */
        List<ReviewResult> getResults(int index, int reviewPattern) {
            ReviewResult[] allResults = ReviewResult.values();
            if (reviewPattern == 3) {
                // Multiple reviews
                return List.of(
                        allResults[index % allResults.length],
                        allResults[(index + 1) % allResults.length]
                );
            }
            // Single review
            return List.of(allResults[index % allResults.length]);
        }
    }

    record StreakScenario(int[] daysWithReviews, int expectedStreak) {
    }
}
