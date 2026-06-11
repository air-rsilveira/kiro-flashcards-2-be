package com.flashcards.integration;

import com.flashcards.dto.*;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class CardControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @AfterEach
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    private UUID createDeck(String name) {
        CreateDeckRequest request = new CreateDeckRequest(name, "Test deck description");
        ResponseEntity<DeckResponse> response = restTemplate.postForEntity(
                "/api/decks", request, DeckResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private CardResponse createCard(UUID deckId, String front, String back) {
        CreateCardRequest request = new CreateCardRequest(front, back);
        ResponseEntity<CardResponse> response = restTemplate.postForEntity(
                "/api/decks/" + deckId + "/cards", request, CardResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Test
    @DisplayName("Full CRUD lifecycle: create, get, update, delete card")
    void fullCrudLifecycle() {
        // Create a deck first
        UUID deckId = createDeck("CRUD Test Deck");

        // CREATE card (POST 201)
        CreateCardRequest createRequest = new CreateCardRequest("What is Java?", "A programming language");
        ResponseEntity<CardResponse> createResponse = restTemplate.postForEntity(
                "/api/decks/" + deckId + "/cards", createRequest, CardResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CardResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.deckId()).isEqualTo(deckId);
        assertThat(created.front()).isEqualTo("What is Java?");
        assertThat(created.back()).isEqualTo("A programming language");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        // GET card (GET 200)
        ResponseEntity<CardResponse> getResponse = restTemplate.getForEntity(
                "/api/cards/" + created.id(), CardResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse fetched = getResponse.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.front()).isEqualTo("What is Java?");
        assertThat(fetched.back()).isEqualTo("A programming language");

        // UPDATE card (PUT 200)
        UpdateCardRequest updateRequest = new UpdateCardRequest("What is Java 21?", "A modern programming language");
        ResponseEntity<CardResponse> updateResponse = restTemplate.exchange(
                "/api/cards/" + created.id(), HttpMethod.PUT,
                new HttpEntity<>(updateRequest), CardResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CardResponse updated = updateResponse.getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.front()).isEqualTo("What is Java 21?");
        assertThat(updated.back()).isEqualTo("A modern programming language");
        // Compare truncated to millis to avoid nanosecond precision differences in JSON serialization
        assertThat(updated.createdAt().toEpochMilli()).isEqualTo(created.createdAt().toEpochMilli());
        assertThat(updated.deckId()).isEqualTo(deckId);

        // DELETE card (DELETE 204)
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/cards/" + created.id(), HttpMethod.DELETE,
                null, Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify gone (GET 404)
        ResponseEntity<String> goneResponse = restTemplate.getForEntity(
                "/api/cards/" + created.id(), String.class);

        assertThat(goneResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Search filter: case-insensitive substring match on front and back")
    void searchFilter() {
        UUID deckId = createDeck("Search Test Deck");

        // Create cards with different content
        createCard(deckId, "What is Java?", "A language by Oracle");
        createCard(deckId, "What is Python?", "A language by PSF");
        createCard(deckId, "What is Spring?", "A Java framework");

        // Search for "java" - should match cards with "Java" in front or back
        ResponseEntity<String> searchResponse = restTemplate.getForEntity(
                "/api/decks/" + deckId + "/cards?q=java", String.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Use RestPageResponse to check content
        ResponseEntity<Map<String, Object>> typedResponse = restTemplate.exchange(
                "/api/decks/" + deckId + "/cards?q=java",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(typedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = typedResponse.getBody();
        assertThat(body).isNotNull();
        // "Java" appears in cards: "What is Java?" and "A Java framework"
        assertThat((Integer) body.get("totalElements")).isEqualTo(2);

        // Case-insensitive search: "JAVA" should return same results
        ResponseEntity<Map<String, Object>> uppercaseResponse = restTemplate.exchange(
                "/api/decks/" + deckId + "/cards?q=JAVA",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(uppercaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> uppercaseBody = uppercaseResponse.getBody();
        assertThat(uppercaseBody).isNotNull();
        assertThat((Integer) uppercaseBody.get("totalElements")).isEqualTo(2);

        // Blank q should return all cards
        ResponseEntity<Map<String, Object>> blankQResponse = restTemplate.exchange(
                "/api/decks/" + deckId + "/cards?q=",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(blankQResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> blankQBody = blankQResponse.getBody();
        assertThat(blankQBody).isNotNull();
        assertThat((Integer) blankQBody.get("totalElements")).isEqualTo(3);
    }

    @Test
    @DisplayName("Card creation with non-existent deck returns 404")
    void createCardWithNonExistentDeck() {
        UUID randomDeckId = UUID.randomUUID();

        CreateCardRequest request = new CreateCardRequest("Front", "Back");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/decks/" + randomDeckId + "/cards", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Pagination: page and size parameters work correctly")
    void pagination() {
        UUID deckId = createDeck("Pagination Test Deck");

        // Create 5 cards
        for (int i = 1; i <= 5; i++) {
            createCard(deckId, "Front " + i, "Back " + i);
        }

        // Request page 0, size 2
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/decks/" + deckId + "/cards?page=0&size=2",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat((Integer) body.get("totalElements")).isEqualTo(5);
        assertThat((Integer) body.get("totalPages")).isEqualTo(3);
        assertThat((Integer) body.get("number")).isEqualTo(0);
        assertThat((Integer) body.get("size")).isEqualTo(2);
        assertThat((java.util.List<?>) body.get("content")).hasSize(2);
    }

    @Test
    @DisplayName("Validation: blank front on create returns 400")
    void validationBlankFrontOnCreate() {
        UUID deckId = createDeck("Validation Test Deck");

        CreateCardRequest request = new CreateCardRequest("   ", "Valid back");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/decks/" + deckId + "/cards", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Validation: blank back on update returns 400")
    void validationBlankBackOnUpdate() {
        UUID deckId = createDeck("Validation Test Deck");
        CardResponse card = createCard(deckId, "Valid front", "Valid back");

        UpdateCardRequest updateRequest = new UpdateCardRequest("Valid front", "   ");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cards/" + card.id(), HttpMethod.PUT,
                new HttpEntity<>(updateRequest), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
