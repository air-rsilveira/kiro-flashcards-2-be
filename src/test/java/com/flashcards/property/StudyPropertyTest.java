package com.flashcards.property;

import com.flashcards.dto.CardResponse;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Property-based tests for Study endpoint.
 * Validates: Requirements 11.1, 11.3, 11.4, 11.5, 11.6
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudyPropertyTest {

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
     * Property 8: Study Set Correctness
     *
     * For any Deck containing Cards with varying review states, the study endpoint SHALL return
     * only Cards that either (a) have never been reviewed, or (b) have their most recent Review's
     * nextReviewAt <= current server time. No Card with nextReviewAt in the future SHALL appear.
     *
     * Validates: Requirements 11.1, 11.3, 11.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 8: Study Set Correctness")
    void studySetContainsOnlyEligibleCards(@ForAll("cardCounts") int cardCount,
                                           @ForAll("reviewScenarios") ReviewScenario scenario) {
        // Create deck
        Deck deck = createDeck("Study Test Deck");

        Instant now = Instant.now();

        // Create cards
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            cards.add(createCard(deck.getId(), "Front " + i, "Back " + i));
        }

        if (cards.isEmpty()) {
            // With no cards, study should return empty
            List<CardResponse> studyCards = getStudyCards(deck.getId());
            assert studyCards.isEmpty() : "Expected empty study set for deck with no cards";
            return;
        }

        // Assign review states based on scenario
        Set<UUID> neverReviewedIds = new HashSet<>();
        Set<UUID> dueIds = new HashSet<>();
        Set<UUID> futureIds = new HashSet<>();

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int state = scenario.getState(i, cards.size());

            switch (state) {
                case 0 -> // Never reviewed
                        neverReviewedIds.add(card.getId());
                case 1 -> {
                    // Due (nextReviewAt in the past)
                    dueIds.add(card.getId());
                    Instant reviewedAt = now.minus(2, ChronoUnit.HOURS);
                    Instant nextReviewAt = now.minus(30, ChronoUnit.MINUTES);
                    createReview(card.getId(), ReviewResult.HARD, reviewedAt, nextReviewAt);
                }
                case 2 -> {
                    // Future (nextReviewAt in the future)
                    futureIds.add(card.getId());
                    Instant reviewedAt = now.minus(1, ChronoUnit.MINUTES);
                    Instant nextReviewAt = now.plus(2, ChronoUnit.DAYS);
                    createReview(card.getId(), ReviewResult.EASY, reviewedAt, nextReviewAt);
                }
            }
        }

        // Call study endpoint
        List<CardResponse> studyCards = getStudyCards(deck.getId());

        // Verify: every returned card is either never-reviewed or due
        for (CardResponse cr : studyCards) {
            boolean isNeverReviewed = neverReviewedIds.contains(cr.id());
            boolean isDue = dueIds.contains(cr.id());
            assert isNeverReviewed || isDue :
                    String.format("Card %s is in study set but is neither never-reviewed nor due", cr.id());
        }

        // Verify: no future-scheduled cards appear
        for (CardResponse cr : studyCards) {
            assert !futureIds.contains(cr.id()) :
                    String.format("Card %s has nextReviewAt in the future but appeared in study set", cr.id());
        }
    }

    /**
     * Property 9: Study Set Ordering
     *
     * For any study result set, Cards never reviewed SHALL appear before Cards with past nextReviewAt.
     * Among never-reviewed Cards, they SHALL be ordered by createdAt ascending.
     * Among due Cards, they SHALL be ordered by nextReviewAt ascending.
     *
     * Validates: Requirements 11.6
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 9: Study Set Ordering")
    void studySetIsCorrectlyOrdered(@ForAll("orderingCardCounts") int cardCount) {
        Deck deck = createDeck("Ordering Test Deck");
        Instant now = Instant.now();

        // Create cards with slight delays to ensure distinct createdAt values
        List<Card> neverReviewedCards = new ArrayList<>();
        List<Card> dueCards = new ArrayList<>();

        // Create never-reviewed cards (first half)
        int neverReviewedCount = Math.max(1, cardCount / 2);
        for (int i = 0; i < neverReviewedCount; i++) {
            Card card = createCard(deck.getId(), "NR Front " + i, "NR Back " + i);
            neverReviewedCards.add(card);
        }

        // Create due cards (second half) with different nextReviewAt values
        int dueCount = cardCount - neverReviewedCount;
        for (int i = 0; i < dueCount; i++) {
            Card card = createCard(deck.getId(), "Due Front " + i, "Due Back " + i);
            dueCards.add(card);
            // Set different past nextReviewAt values - more recent as i increases
            Instant reviewedAt = now.minus(10, ChronoUnit.HOURS);
            Instant nextReviewAt = now.minus((dueCount - i) * 10L, ChronoUnit.MINUTES);
            createReview(card.getId(), ReviewResult.HARD, reviewedAt, nextReviewAt);
        }

        List<CardResponse> studyCards = getStudyCards(deck.getId());

        if (studyCards.size() <= 1) {
            return; // Nothing to verify for 0 or 1 card
        }

        // Identify boundary between never-reviewed and due cards
        Set<UUID> neverReviewedIds = new HashSet<>();
        for (Card c : neverReviewedCards) {
            neverReviewedIds.add(c.getId());
        }

        // Verify: all never-reviewed cards come before all due cards
        boolean seenDue = false;
        for (CardResponse cr : studyCards) {
            if (neverReviewedIds.contains(cr.id())) {
                assert !seenDue : "Never-reviewed card appeared after a due card in study set";
            } else {
                seenDue = true;
            }
        }

        // Verify: among never-reviewed cards, createdAt is ascending
        List<CardResponse> neverReviewedInResult = studyCards.stream()
                .filter(cr -> neverReviewedIds.contains(cr.id()))
                .toList();
        for (int i = 1; i < neverReviewedInResult.size(); i++) {
            Instant prev = neverReviewedInResult.get(i - 1).createdAt();
            Instant curr = neverReviewedInResult.get(i).createdAt();
            assert !prev.isAfter(curr) :
                    String.format("Never-reviewed cards not sorted by createdAt asc: %s > %s", prev, curr);
        }

        // Verify: among due cards, nextReviewAt is ascending
        // We need to check the ordering of the due cards. We stored the nextReviewAt values,
        // so we verify the order based on their position in dueCards list (which maps to nextReviewAt order).
        Set<UUID> dueIds = new HashSet<>();
        Map<UUID, Instant> dueNextReviewAt = new HashMap<>();
        for (int i = 0; i < dueCards.size(); i++) {
            Card c = dueCards.get(i);
            dueIds.add(c.getId());
            Instant nextReviewAt = now.minus((dueCount - i) * 10L, ChronoUnit.MINUTES);
            dueNextReviewAt.put(c.getId(), nextReviewAt);
        }

        List<CardResponse> dueInResult = studyCards.stream()
                .filter(cr -> dueIds.contains(cr.id()))
                .toList();
        for (int i = 1; i < dueInResult.size(); i++) {
            Instant prevNext = dueNextReviewAt.get(dueInResult.get(i - 1).id());
            Instant currNext = dueNextReviewAt.get(dueInResult.get(i).id());
            if (prevNext != null && currNext != null) {
                assert !prevNext.isAfter(currNext) :
                        String.format("Due cards not sorted by nextReviewAt asc: %s > %s", prevNext, currNext);
            }
        }
    }

    /**
     * Property 10: Study Set Size Limit
     *
     * For any Deck regardless of how many Cards are due, the study endpoint SHALL return
     * at most 20 Cards.
     *
     * Validates: Requirements 11.5
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 10: Study Set Size Limit")
    void studySetReturnsAtMost20Cards(@ForAll("largeDeckSizes") int cardCount) {
        Deck deck = createDeck("Size Limit Test Deck");

        // Create all cards (never reviewed, so all eligible for study)
        for (int i = 0; i < cardCount; i++) {
            createCard(deck.getId(), "Front " + i, "Back " + i);
        }

        List<CardResponse> studyCards = getStudyCards(deck.getId());

        assert studyCards.size() <= 20 :
                String.format("Study set returned %d cards, expected at most 20", studyCards.size());

        // If there are more than 20 eligible cards, exactly 20 should be returned
        if (cardCount > 20) {
            assert studyCards.size() == 20 :
                    String.format("With %d eligible cards, expected exactly 20 but got %d",
                            cardCount, studyCards.size());
        }
    }

    // --- Arbitraries / Providers ---

    @Provide
    Arbitrary<Integer> cardCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Integer> orderingCardCounts() {
        return Arbitraries.integers().between(2, 10);
    }

    @Provide
    Arbitrary<Integer> largeDeckSizes() {
        return Arbitraries.integers().between(21, 35);
    }

    @Provide
    Arbitrary<ReviewScenario> reviewScenarios() {
        return Arbitraries.of(
                new ReviewScenario(new int[]{0, 1, 2}),       // mix of all states
                new ReviewScenario(new int[]{0, 0, 0}),       // all never reviewed
                new ReviewScenario(new int[]{1, 1, 1}),       // all due
                new ReviewScenario(new int[]{2, 2, 2}),       // all future
                new ReviewScenario(new int[]{0, 2, 1}),       // mixed order
                new ReviewScenario(new int[]{1, 0, 2, 0, 1})  // larger mix
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

    private List<CardResponse> getStudyCards(UUID deckId) {
        ResponseEntity<List<CardResponse>> response = restTemplate.exchange(
                "/api/decks/" + deckId + "/study",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CardResponse>>() {}
        );
        assert response.getStatusCode().is2xxSuccessful() :
                "Expected 200 OK but got " + response.getStatusCode();
        return response.getBody() != null ? response.getBody() : List.of();
    }

    // --- Inner classes ---

    record ReviewScenario(int[] states) {
        /**
         * Returns the state for a given card index.
         * 0 = never reviewed, 1 = due (past), 2 = future
         */
        int getState(int index, int totalCards) {
            return states[index % states.length];
        }
    }
}
