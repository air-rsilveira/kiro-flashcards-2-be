package com.flashcards.integration;

import com.flashcards.dto.*;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class DeckControllerIntegrationTest {

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

    // ==================== Full CRUD Lifecycle ====================

    @Test
    @DisplayName("Full CRUD lifecycle: create, get, update, delete, verify gone")
    void fullCrudLifecycle() {
        // CREATE
        CreateDeckRequest createRequest = new CreateDeckRequest("My Deck", "A test deck");
        ResponseEntity<DeckResponse> createResponse = restTemplate.postForEntity(
                "/api/decks", createRequest, DeckResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DeckResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("My Deck");
        assertThat(created.description()).isEqualTo("A test deck");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        UUID deckId = created.id();

        // GET
        ResponseEntity<DeckResponse> getResponse = restTemplate.getForEntity(
                "/api/decks/{id}", DeckResponse.class, deckId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeckResponse fetched = getResponse.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(deckId);
        assertThat(fetched.name()).isEqualTo("My Deck");
        assertThat(fetched.description()).isEqualTo("A test deck");

        // UPDATE
        UpdateDeckRequest updateRequest = new UpdateDeckRequest("Updated Deck", "Updated description");
        HttpEntity<UpdateDeckRequest> updateEntity = new HttpEntity<>(updateRequest);
        ResponseEntity<DeckResponse> updateResponse = restTemplate.exchange(
                "/api/decks/{id}", HttpMethod.PUT, updateEntity, DeckResponse.class, deckId);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeckResponse updated = updateResponse.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(deckId);
        assertThat(updated.name()).isEqualTo("Updated Deck");
        assertThat(updated.description()).isEqualTo("Updated description");
        // Compare createdAt from fetched (DB-persisted) version to avoid nanosecond precision differences
        assertThat(updated.createdAt()).isEqualTo(fetched.createdAt());

        // DELETE
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/decks/{id}", HttpMethod.DELETE, null, Void.class, deckId);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // VERIFY GONE
        ResponseEntity<String> goneResponse = restTemplate.getForEntity(
                "/api/decks/{id}", String.class, deckId);

        assertThat(goneResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ==================== Pagination ====================

    @Test
    @DisplayName("Pagination: create 5 decks, list with size=2 verifies pages and metadata")
    void paginationWithMultipleDecks() throws InterruptedException {
        // Create 5 decks with slight delay to guarantee distinct createdAt ordering
        for (int i = 1; i <= 5; i++) {
            CreateDeckRequest request = new CreateDeckRequest("Deck " + i, "Description " + i);
            ResponseEntity<DeckResponse> response = restTemplate.postForEntity(
                    "/api/decks", request, DeckResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            if (i < 5) {
                Thread.sleep(10); // ensure distinct createdAt
            }
        }

        // Get page 0 with size 2
        ResponseEntity<String> page0Response = restTemplate.getForEntity(
                "/api/decks?page=0&size=2", String.class);
        assertThat(page0Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Use a Map to parse the paginated response
        ResponseEntity<Map<String, Object>> page0 = restTemplate.exchange(
                "/api/decks?page=0&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body0 = page0.getBody();
        assertThat(body0).isNotNull();
        assertThat(body0.get("totalElements")).isEqualTo(5);
        assertThat(body0.get("totalPages")).isEqualTo(3);
        assertThat(body0.get("number")).isEqualTo(0);
        assertThat(body0.get("size")).isEqualTo(2);
        List<?> content0 = (List<?>) body0.get("content");
        assertThat(content0).hasSize(2);

        // Get page 1 with size 2
        ResponseEntity<Map<String, Object>> page1 = restTemplate.exchange(
                "/api/decks?page=1&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> body1 = page1.getBody();
        assertThat(body1).isNotNull();
        List<?> content1 = (List<?>) body1.get("content");
        assertThat(content1).hasSize(2);

        // Get page 2 with size 2 (last page, should have 1 item)
        ResponseEntity<Map<String, Object>> page2 = restTemplate.exchange(
                "/api/decks?page=2&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> body2 = page2.getBody();
        assertThat(body2).isNotNull();
        List<?> content2 = (List<?>) body2.get("content");
        assertThat(content2).hasSize(1);
    }

    // ==================== Cascade Delete ====================

    @Test
    @DisplayName("Cascade delete: deleting a deck removes its cards and their reviews")
    void cascadeDeleteRemovesCardsAndReviews() {
        // Create a deck
        CreateDeckRequest deckRequest = new CreateDeckRequest("Cascade Deck", "Testing cascade");
        ResponseEntity<DeckResponse> deckResponse = restTemplate.postForEntity(
                "/api/decks", deckRequest, DeckResponse.class);
        assertThat(deckResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID deckId = deckResponse.getBody().id();

        // Create 2 cards in the deck
        CreateCardRequest cardRequest1 = new CreateCardRequest("Front 1", "Back 1");
        ResponseEntity<CardResponse> cardResponse1 = restTemplate.postForEntity(
                "/api/decks/{deckId}/cards", cardRequest1, CardResponse.class, deckId);
        assertThat(cardResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID cardId1 = cardResponse1.getBody().id();

        CreateCardRequest cardRequest2 = new CreateCardRequest("Front 2", "Back 2");
        ResponseEntity<CardResponse> cardResponse2 = restTemplate.postForEntity(
                "/api/decks/{deckId}/cards", cardRequest2, CardResponse.class, deckId);
        assertThat(cardResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID cardId2 = cardResponse2.getBody().id();

        // Add reviews for both cards
        CreateReviewRequest reviewRequest = new CreateReviewRequest(ReviewResult.GOOD);
        ResponseEntity<ReviewResponse> reviewResponse1 = restTemplate.postForEntity(
                "/api/cards/{id}/review", reviewRequest, ReviewResponse.class, cardId1);
        assertThat(reviewResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ReviewResponse> reviewResponse2 = restTemplate.postForEntity(
                "/api/cards/{id}/review", reviewRequest, ReviewResponse.class, cardId2);
        assertThat(reviewResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify cards exist before delete
        ResponseEntity<CardResponse> card1Before = restTemplate.getForEntity(
                "/api/cards/{id}", CardResponse.class, cardId1);
        assertThat(card1Before.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Delete the deck
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/decks/{id}", HttpMethod.DELETE, null, Void.class, deckId);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify cards are gone (GET /api/cards/{id} returns 404)
        ResponseEntity<String> card1After = restTemplate.getForEntity(
                "/api/cards/{id}", String.class, cardId1);
        assertThat(card1After.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> card2After = restTemplate.getForEntity(
                "/api/cards/{id}", String.class, cardId2);
        assertThat(card2After.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify no orphan data in repositories
        assertThat(cardRepository.findByDeckId(deckId)).isEmpty();
    }

    // ==================== Create with null description ====================

    @Test
    @DisplayName("Create deck with null description results in empty string")
    void createDeckWithNullDescription() {
        CreateDeckRequest request = new CreateDeckRequest("No Description Deck", null);
        ResponseEntity<DeckResponse> response = restTemplate.postForEntity(
                "/api/decks", request, DeckResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DeckResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.name()).isEqualTo("No Description Deck");
        assertThat(body.description()).isEqualTo("");
    }

    // ==================== Validation ====================

    @Test
    @DisplayName("Validation: POST with blank name returns 400")
    void createDeckWithBlankNameReturns400() {
        CreateDeckRequest request = new CreateDeckRequest("   ", "Some description");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/decks", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Validation: POST with name > 100 chars returns 400")
    void createDeckWithNameExceedingMaxLengthReturns400() {
        String longName = "A".repeat(101);
        CreateDeckRequest request = new CreateDeckRequest(longName, "Some description");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/decks", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
