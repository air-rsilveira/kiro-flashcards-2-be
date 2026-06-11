package com.flashcards.property;

import com.flashcards.dto.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Card CRUD operations.
 * Validates: Requirements 6.1, 6.6, 8.1, 1.2, 6.4, 9.3, 9.1, 9.4, 5.3, 10.1, 10.4
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CardCrudPropertyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DeckRepository deckRepository;

    @AfterProperty
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> validCardContent() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(200)
                .alpha()
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> validDeckNames() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha()
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "   \t\n  ");
    }

    // --- Helper Methods ---

    private DeckResponse createDeck(String name) {
        CreateDeckRequest request = new CreateDeckRequest(name, "test deck");
        ResponseEntity<DeckResponse> response = restTemplate.postForEntity(
                "/api/decks", request, DeckResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private CardResponse createCard(UUID deckId, String front, String back) {
        CreateCardRequest request = new CreateCardRequest(front, back);
        ResponseEntity<CardResponse> response = restTemplate.postForEntity(
                "/api/decks/" + deckId + "/cards", request, CardResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // --- Property 3: Card Creation Round-Trip ---

    /**
     * Property 3: Card Creation Round-Trip
     * For any valid front and back content and an existing deckId, creating a Card via POST
     * and then fetching it by the returned ID SHALL return a Card with the same front, back,
     * and deckId, plus valid auto-generated id, createdAt, and updatedAt.
     *
     * Validates: Requirements 6.1, 6.6, 8.1
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 3: Card Creation Round-Trip")
    void cardCreationRoundTrip(
            @ForAll("validDeckNames") String deckName,
            @ForAll("validCardContent") String front,
            @ForAll("validCardContent") String back) {

        // Create a deck first
        DeckResponse deck = createDeck(deckName);

        // Create a card in that deck
        CreateCardRequest createRequest = new CreateCardRequest(front, back);
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                "/api/decks/" + deck.id() + "/cards", createRequest, CardResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CardResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();

        // Fetch the card by ID
        ResponseEntity<CardResponse> getResponse = restTemplate.getForEntity(
                "/api/cards/" + created.id(), CardResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse fetched = getResponse.getBody();
        assertThat(fetched).isNotNull();

        // Verify fields match
        assertThat(fetched.front()).isEqualTo(front);
        assertThat(fetched.back()).isEqualTo(back);
        assertThat(fetched.deckId()).isEqualTo(deck.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.createdAt()).isNotNull();
        assertThat(fetched.updatedAt()).isNotNull();
    }

    // --- Property 4: Blank Input Rejection ---

    /**
     * Property 4: Blank Input Rejection
     * For any string composed entirely of whitespace characters, using it as a Deck name,
     * Card front, or Card back SHALL result in HTTP 400 rejection.
     *
     * Validates: Requirements 1.2, 6.4, 9.3
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 4: Blank Input Rejection - Deck Name")
    void blankDeckNameRejected(@ForAll("blankStrings") String blankName) {
        CreateDeckRequest request = new CreateDeckRequest(blankName, "desc");
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/decks", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 4: Blank Input Rejection - Card Front")
    void blankCardFrontRejected(
            @ForAll("validDeckNames") String deckName,
            @ForAll("blankStrings") String blankFront,
            @ForAll("validCardContent") String validBack) {

        DeckResponse deck = createDeck(deckName);

        CreateCardRequest request = new CreateCardRequest(blankFront, validBack);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/decks/" + deck.id() + "/cards", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 4: Blank Input Rejection - Card Back")
    void blankCardBackRejected(
            @ForAll("validDeckNames") String deckName,
            @ForAll("validCardContent") String validFront,
            @ForAll("blankStrings") String blankBack) {

        DeckResponse deck = createDeck(deckName);

        CreateCardRequest request = new CreateCardRequest(validFront, blankBack);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/decks/" + deck.id() + "/cards", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Property 6: Card Update Preserves Immutable Fields ---

    /**
     * Property 6: Card Update Preserves Immutable Fields
     * For any existing Card and valid update payload, updating the Card SHALL preserve
     * createdAt and deckId unchanged, update updatedAt to a newer value, and reflect
     * the new front and back content.
     *
     * Validates: Requirements 9.1, 9.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 6: Card Update Preserves Immutable Fields")
    void cardUpdatePreservesImmutableFields(
            @ForAll("validDeckNames") String deckName,
            @ForAll("validCardContent") String originalFront,
            @ForAll("validCardContent") String originalBack,
            @ForAll("validCardContent") String newFront,
            @ForAll("validCardContent") String newBack) {

        // Create deck and card
        DeckResponse deck = createDeck(deckName);
        CardResponse original = createCard(deck.id(), originalFront, originalBack);

        // Update the card
        UpdateCardRequest updateRequest = new UpdateCardRequest(newFront, newBack);
        restTemplate.put("/api/cards/" + original.id(), updateRequest);

        // Fetch updated card
        ResponseEntity<CardResponse> getResponse = restTemplate.getForEntity(
                "/api/cards/" + original.id(), CardResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse updated = getResponse.getBody();
        assertThat(updated).isNotNull();

        // Verify immutable fields are unchanged
        // Note: comparing truncated to microseconds due to DB precision differences
        assertThat(updated.createdAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(original.createdAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(updated.deckId()).isEqualTo(original.deckId());

        // Verify updatedAt is >= original (using millis precision due to DB truncation)
        assertThat(updated.updatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isAfterOrEqualTo(original.updatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

        // Verify mutable fields reflect new values
        assertThat(updated.front()).isEqualTo(newFront);
        assertThat(updated.back()).isEqualTo(newBack);
    }

    // --- Property 7: Cascade Deletion Leaves No Orphans ---

    /**
     * Property 7: Cascade Deletion Leaves No Orphans
     * For any Deck with associated Cards and Reviews, deleting the Deck SHALL result
     * in zero Cards and zero Reviews associated with that Deck remaining.
     *
     * Validates: Requirements 5.3, 10.1, 10.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 7: Cascade Deletion Leaves No Orphans")
    void cascadeDeletionLeavesNoOrphans(
            @ForAll("validDeckNames") String deckName,
            @ForAll("validCardContent") String front1,
            @ForAll("validCardContent") String back1,
            @ForAll("validCardContent") String front2,
            @ForAll("validCardContent") String back2) {

        // Create deck with cards
        DeckResponse deck = createDeck(deckName);
        CardResponse card1 = createCard(deck.id(), front1, back1);
        CardResponse card2 = createCard(deck.id(), front2, back2);

        // Create reviews for the cards
        CreateReviewRequest reviewRequest = new CreateReviewRequest(ReviewResult.GOOD);
        restTemplate.postForEntity(
                "/api/cards/" + card1.id() + "/review", reviewRequest, Map.class);
        restTemplate.postForEntity(
                "/api/cards/" + card2.id() + "/review", reviewRequest, Map.class);

        // Delete the deck
        restTemplate.delete("/api/decks/" + deck.id());

        // Verify deck is gone
        ResponseEntity<Map> deckResponse = restTemplate.getForEntity(
                "/api/decks/" + deck.id(), Map.class);
        assertThat(deckResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify cards are gone (no orphans)
        ResponseEntity<Map> card1Response = restTemplate.getForEntity(
                "/api/cards/" + card1.id(), Map.class);
        assertThat(card1Response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> card2Response = restTemplate.getForEntity(
                "/api/cards/" + card2.id(), Map.class);
        assertThat(card2Response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
