package com.flashcards.integration;

import com.flashcards.dto.CardResponse;
import com.flashcards.dto.CreateReviewRequest;
import com.flashcards.dto.ReviewResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class StudyReviewIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @BeforeEach
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    // --- Study Endpoint Tests ---

    @Test
    void studyReturnsNeverReviewedCards() {
        Deck deck = createDeck("Study Deck");
        Card card1 = createCard(deck.getId(), "Q1", "A1");
        Card card2 = createCard(deck.getId(), "Q2", "A2");

        ResponseEntity<List<CardResponse>> response = restTemplate.exchange(
                "/api/decks/{deckId}/study",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                deck.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CardResponse> cards = response.getBody();
        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(CardResponse::id)
                .containsExactlyInAnyOrder(card1.getId(), card2.getId());
    }

    @Test
    void studyExcludesCardsWithFutureNextReviewAt() {
        Deck deck = createDeck("Study Deck");
        Card card = createCard(deck.getId(), "Q1", "A1");

        // Add a review with nextReviewAt far in the future
        Review review = new Review();
        review.setCardId(card.getId());
        review.setResult(ReviewResult.EASY);
        review.setReviewedAt(Instant.now());
        review.setNextReviewAt(Instant.now().plus(30, ChronoUnit.DAYS));
        reviewRepository.save(review);

        ResponseEntity<List<CardResponse>> response = restTemplate.exchange(
                "/api/decks/{deckId}/study",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                deck.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void studyIncludesDueCards() {
        Deck deck = createDeck("Study Deck");
        Card card = createCard(deck.getId(), "Q1", "A1");

        // Add a review with nextReviewAt in the past
        Review review = new Review();
        review.setCardId(card.getId());
        review.setResult(ReviewResult.GOOD);
        review.setReviewedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        review.setNextReviewAt(Instant.now().minus(1, ChronoUnit.DAYS));
        reviewRepository.save(review);

        ResponseEntity<List<CardResponse>> response = restTemplate.exchange(
                "/api/decks/{deckId}/study",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                deck.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CardResponse> cards = response.getBody();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).id()).isEqualTo(card.getId());
    }

    @Test
    void studyOrderingNeverReviewedFirst() {
        Deck deck = createDeck("Study Deck");

        // Create two never-reviewed cards
        Card neverReviewed1 = createCard(deck.getId(), "NR1", "A1");
        Card neverReviewed2 = createCard(deck.getId(), "NR2", "A2");

        // Create a card that is due (has a review with nextReviewAt in the past)
        Card dueCard = createCard(deck.getId(), "Due", "ADue");
        Review review = new Review();
        review.setCardId(dueCard.getId());
        review.setResult(ReviewResult.HARD);
        review.setReviewedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        review.setNextReviewAt(Instant.now().minus(1, ChronoUnit.HOURS));
        reviewRepository.save(review);

        ResponseEntity<List<CardResponse>> response = restTemplate.exchange(
                "/api/decks/{deckId}/study",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                deck.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<CardResponse> cards = response.getBody();
        assertThat(cards).hasSize(3);

        // Never-reviewed cards come first (ordered by createdAt asc)
        assertThat(cards.get(0).id()).isEqualTo(neverReviewed1.getId());
        assertThat(cards.get(1).id()).isEqualTo(neverReviewed2.getId());
        // Due card comes last
        assertThat(cards.get(2).id()).isEqualTo(dueCard.getId());
    }

    @Test
    void studyReturns404ForNonExistentDeck() {
        UUID randomId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/decks/{deckId}/study",
                String.class,
                randomId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Review Endpoint Tests ---

    @Test
    void reviewRegistrationWithGoodResult() {
        Deck deck = createDeck("Review Deck");
        Card card = createCard(deck.getId(), "Q1", "A1");

        CreateReviewRequest request = new CreateReviewRequest(ReviewResult.GOOD);

        ResponseEntity<ReviewResponse> response = restTemplate.postForEntity(
                "/api/cards/{id}/review",
                request,
                ReviewResponse.class,
                card.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ReviewResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.cardId()).isEqualTo(card.getId());
        assertThat(body.result()).isEqualTo(ReviewResult.GOOD);
        assertThat(body.reviewedAt()).isNotNull();
        assertThat(body.nextReviewAt()).isNotNull();

        // GOOD result: nextReviewAt = reviewedAt + 1 day
        Instant expectedNext = body.reviewedAt().plus(1, ChronoUnit.DAYS);
        assertThat(body.nextReviewAt()).isEqualTo(expectedNext);
    }

    @Test
    void reviewRegistrationWithAgainResult() {
        Deck deck = createDeck("Review Deck");
        Card card = createCard(deck.getId(), "Q1", "A1");

        CreateReviewRequest request = new CreateReviewRequest(ReviewResult.AGAIN);

        ResponseEntity<ReviewResponse> response = restTemplate.postForEntity(
                "/api/cards/{id}/review",
                request,
                ReviewResponse.class,
                card.getId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ReviewResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.cardId()).isEqualTo(card.getId());
        assertThat(body.result()).isEqualTo(ReviewResult.AGAIN);
        assertThat(body.reviewedAt()).isNotNull();
        assertThat(body.nextReviewAt()).isNotNull();

        // AGAIN result: nextReviewAt = reviewedAt + 1 minute
        Instant expectedNext = body.reviewedAt().plus(1, ChronoUnit.MINUTES);
        assertThat(body.nextReviewAt()).isEqualTo(expectedNext);
    }

    @Test
    void reviewReturns404ForNonExistentCard() {
        UUID randomId = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest(ReviewResult.GOOD);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/cards/{id}/review",
                request,
                String.class,
                randomId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Helper Methods ---

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
}
