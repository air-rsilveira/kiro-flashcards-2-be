package com.flashcards.integration;

import com.flashcards.dto.ResultDistribution;
import com.flashcards.dto.StatsResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatsControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @AfterEach
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    /**
     * Validates: Requirement 13.5
     * IF the Deck has no Cards, stats should return all zeros.
     */
    @Test
    void emptyDeckReturnsAllZeros() {
        Deck deck = createDeck("Empty Deck");

        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", StatsResponse.class, deck.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.totalCards()).isZero();
        assertThat(stats.cardsStudied()).isZero();
        assertThat(stats.cardsDue()).isZero();
        assertThat(stats.totalReviews()).isZero();
        assertThat(stats.studyStreak()).isZero();
        assertThat(stats.resultDistribution()).isEqualTo(new ResultDistribution(0, 0, 0, 0));
    }

    /**
     * Validates: Requirements 13.1, 13.3
     * Stats with mixed review states: totalCards, cardsStudied, totalReviews, resultDistribution.
     */
    @Test
    void statsWithMixedReviewStates() {
        Deck deck = createDeck("Study Deck");
        Card card1 = createCard(deck.getId(), "Q1", "A1");
        Card card2 = createCard(deck.getId(), "Q2", "A2");
        Card card3 = createCard(deck.getId(), "Q3", "A3");
        Card card4 = createCard(deck.getId(), "Q4", "A4");
        Card card5 = createCard(deck.getId(), "Q5", "A5");

        // Review card1 with EASY, card2 with GOOD, card3 with HARD, card4 with AGAIN
        // card5 is never reviewed
        Instant now = Instant.now();
        createReview(card1.getId(), ReviewResult.EASY, now.minus(1, ChronoUnit.HOURS));
        createReview(card2.getId(), ReviewResult.GOOD, now.minus(2, ChronoUnit.HOURS));
        createReview(card3.getId(), ReviewResult.HARD, now.minus(30, ChronoUnit.MINUTES));
        createReview(card4.getId(), ReviewResult.AGAIN, now.minus(10, ChronoUnit.MINUTES));
        // Second review on card1
        createReview(card1.getId(), ReviewResult.GOOD, now.minus(5, ChronoUnit.MINUTES));

        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", StatsResponse.class, deck.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.totalCards()).isEqualTo(5);
        assertThat(stats.cardsStudied()).isEqualTo(4); // card1-4 have reviews
        assertThat(stats.totalReviews()).isEqualTo(5); // 5 total review records
        assertThat(stats.resultDistribution().easy()).isEqualTo(1);
        assertThat(stats.resultDistribution().good()).isEqualTo(2); // card2 GOOD + card1 second GOOD
        assertThat(stats.resultDistribution().hard()).isEqualTo(1);
        assertThat(stats.resultDistribution().again()).isEqualTo(1);
    }

    /**
     * Validates: Requirement 13.3
     * cardsDue: cards never reviewed + cards with nextReviewAt <= now.
     */
    @Test
    void cardsDueCalculation() {
        Deck deck = createDeck("Due Deck");
        Card neverReviewed = createCard(deck.getId(), "Never", "Reviewed");
        Card dueCard = createCard(deck.getId(), "Due", "Card");
        Card futureCard = createCard(deck.getId(), "Future", "Card");

        Instant now = Instant.now();

        // dueCard: reviewed with nextReviewAt in the past → due
        createReviewWithNextReview(dueCard.getId(), ReviewResult.HARD,
                now.minus(1, ChronoUnit.HOURS), now.minus(30, ChronoUnit.MINUTES));

        // futureCard: reviewed with nextReviewAt in the future → not due
        createReviewWithNextReview(futureCard.getId(), ReviewResult.EASY,
                now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.DAYS));

        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", StatsResponse.class, deck.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.totalCards()).isEqualTo(3);
        // neverReviewed is due + dueCard is due = 2 due
        assertThat(stats.cardsDue()).isEqualTo(2);
    }

    /**
     * Validates: Requirement 13.4
     * studyStreak: consecutive days with at least one review counting backwards from today.
     * Reviews for today and yesterday → streak = 2.
     */
    @Test
    void studyStreakCalculation_consecutiveDays() {
        Deck deck = createDeck("Streak Deck");
        Card card = createCard(deck.getId(), "Front", "Back");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayReviewTime = today.atStartOfDay(ZoneOffset.UTC).toInstant().plus(10, ChronoUnit.HOURS);
        Instant yesterdayReviewTime = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plus(10, ChronoUnit.HOURS);

        createReview(card.getId(), ReviewResult.GOOD, todayReviewTime);
        createReview(card.getId(), ReviewResult.EASY, yesterdayReviewTime);

        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", StatsResponse.class, deck.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.studyStreak()).isEqualTo(2);
    }

    /**
     * Validates: Requirement 13.4
     * studyStreak: if today has no review, streak = 0.
     */
    @Test
    void studyStreakCalculation_noReviewToday() {
        Deck deck = createDeck("No Streak Deck");
        Card card = createCard(deck.getId(), "Front", "Back");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Only a review yesterday, none today
        Instant yesterdayReviewTime = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plus(10, ChronoUnit.HOURS);
        createReview(card.getId(), ReviewResult.GOOD, yesterdayReviewTime);

        ResponseEntity<StatsResponse> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", StatsResponse.class, deck.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatsResponse stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.studyStreak()).isZero();
    }

    /**
     * Validates: Requirement 13.2 (via 14.2)
     * 404 for non-existent deck.
     */
    @Test
    @SuppressWarnings("unchecked")
    void nonExistentDeckReturns404() {
        UUID randomId = UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/stats", Map.class, randomId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(404);
    }

    // === Helper methods ===

    private Deck createDeck(String name) {
        Deck deck = new Deck();
        deck.setName(name);
        deck.setDescription("Test description");
        return deckRepository.save(deck);
    }

    private Card createCard(UUID deckId, String front, String back) {
        Card card = new Card();
        card.setDeckId(deckId);
        card.setFront(front);
        card.setBack(back);
        return cardRepository.save(card);
    }

    private Review createReview(UUID cardId, ReviewResult result, Instant reviewedAt) {
        Instant nextReviewAt = switch (result) {
            case AGAIN -> reviewedAt.plus(1, ChronoUnit.MINUTES);
            case HARD -> reviewedAt.plus(10, ChronoUnit.MINUTES);
            case GOOD -> reviewedAt.plus(1, ChronoUnit.DAYS);
            case EASY -> reviewedAt.plus(4, ChronoUnit.DAYS);
        };
        return createReviewWithNextReview(cardId, result, reviewedAt, nextReviewAt);
    }

    private Review createReviewWithNextReview(UUID cardId, ReviewResult result, Instant reviewedAt, Instant nextReviewAt) {
        Review review = new Review();
        review.setCardId(cardId);
        review.setResult(result);
        review.setReviewedAt(reviewedAt);
        review.setNextReviewAt(nextReviewAt);
        return reviewRepository.save(review);
    }
}
