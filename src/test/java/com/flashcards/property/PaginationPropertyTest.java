package com.flashcards.property;

import com.flashcards.dto.*;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Pagination and Search functionality.
 * Validates: Requirements 2.1, 2.2, 2.5, 7.1, 7.4, 7.7, 7.3
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaginationPropertyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DeckRepository deckRepository;

    @BeforeTry
    void cleanupBefore() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    @AfterProperty
    void cleanup() {
        reviewRepository.deleteAll();
        cardRepository.deleteAll();
        deckRepository.deleteAll();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<Integer> itemCounts() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> pageSizes() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> invalidPages() {
        return Arbitraries.integers().between(-10, -1);
    }

    @Provide
    Arbitrary<Integer> invalidSizeTooLow() {
        return Arbitraries.integers().between(-5, 0);
    }

    @Provide
    Arbitrary<Integer> invalidSizeTooHigh() {
        return Arbitraries.integers().between(101, 200);
    }

    @Provide
    Arbitrary<String> searchTerms() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2)
                .ofMaxLength(5);
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

    // --- Property 11: Pagination Metadata Consistency ---

    /**
     * Property 11: Pagination Metadata Consistency
     * For any paginated listing endpoint (decks or cards), given a total of N items,
     * page P, and size S, the response SHALL satisfy:
     * totalElements == N, totalPages == ceil(N/S), number == P, size == S,
     * and content.length == min(S, N - P*S) when P is valid.
     *
     * Validates: Requirements 2.1, 2.2, 7.1, 7.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 11: Pagination Metadata Consistency - Decks")
    void paginationMetadataConsistencyDecks(
            @ForAll("itemCounts") int n,
            @ForAll("pageSizes") int size) {

        // Create N decks
        for (int i = 0; i < n; i++) {
            createDeck("PaginDeck" + i + UUID.randomUUID().toString().substring(0, 6));
        }

        int totalPages = (int) Math.ceil((double) n / size);

        // Test each valid page
        for (int page = 0; page < totalPages; page++) {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "/api/decks?page=" + page + "&size=" + size,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            int expectedContentLength = Math.min(size, n - page * size);

            assertThat(((Number) body.get("totalElements")).intValue()).isEqualTo(n);
            assertThat(((Number) body.get("totalPages")).intValue()).isEqualTo(totalPages);
            assertThat(((Number) body.get("number")).intValue()).isEqualTo(page);
            assertThat(((Number) body.get("size")).intValue()).isEqualTo(size);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
            assertThat(content).hasSize(expectedContentLength);
        }
    }

    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 11: Pagination Metadata Consistency - Cards")
    void paginationMetadataConsistencyCards(
            @ForAll("itemCounts") int n,
            @ForAll("pageSizes") int size) {

        // Create a deck and N cards
        DeckResponse deck = createDeck("CardPaginDeck" + UUID.randomUUID().toString().substring(0, 6));
        for (int i = 0; i < n; i++) {
            createCard(deck.id(), "Front" + i, "Back" + i);
        }

        int totalPages = (int) Math.ceil((double) n / size);

        // Test each valid page
        for (int page = 0; page < totalPages; page++) {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "/api/decks/" + deck.id() + "/cards?page=" + page + "&size=" + size,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> body = response.getBody();
            assertThat(body).isNotNull();

            int expectedContentLength = Math.min(size, n - page * size);

            assertThat(((Number) body.get("totalElements")).intValue()).isEqualTo(n);
            assertThat(((Number) body.get("totalPages")).intValue()).isEqualTo(totalPages);
            assertThat(((Number) body.get("number")).intValue()).isEqualTo(page);
            assertThat(((Number) body.get("size")).intValue()).isEqualTo(size);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
            assertThat(content).hasSize(expectedContentLength);
        }
    }

    // --- Property 12: Invalid Pagination Rejection ---

    /**
     * Property 12: Invalid Pagination Rejection
     * For any page value < 0 or size value < 1 or size value > 100,
     * the paginated endpoints SHALL return HTTP 400.
     *
     * Validates: Requirements 2.5, 7.7
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 12: Invalid Pagination Rejection - Negative Page")
    void invalidPaginationNegativePageRejected(@ForAll("invalidPages") int invalidPage) {

        // Test /api/decks with invalid page
        ResponseEntity<Map<String, Object>> deckResponse = restTemplate.exchange(
                "/api/decks?page=" + invalidPage + "&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(deckResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test /api/decks/{deckId}/cards with invalid page
        DeckResponse deck = createDeck("InvalidPageDeck" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map<String, Object>> cardResponse = restTemplate.exchange(
                "/api/decks/" + deck.id() + "/cards?page=" + invalidPage + "&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(cardResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 12: Invalid Pagination Rejection - Size Too Low")
    void invalidPaginationSizeTooLowRejected(@ForAll("invalidSizeTooLow") int invalidSize) {

        // Test /api/decks with invalid size
        ResponseEntity<Map<String, Object>> deckResponse = restTemplate.exchange(
                "/api/decks?page=0&size=" + invalidSize,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(deckResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test /api/decks/{deckId}/cards with invalid size
        DeckResponse deck = createDeck("InvalidSizeDeck" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map<String, Object>> cardResponse = restTemplate.exchange(
                "/api/decks/" + deck.id() + "/cards?page=0&size=" + invalidSize,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(cardResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 12: Invalid Pagination Rejection - Size Too High")
    void invalidPaginationSizeTooHighRejected(@ForAll("invalidSizeTooHigh") int invalidSize) {

        // Test /api/decks with invalid size
        ResponseEntity<Map<String, Object>> deckResponse = restTemplate.exchange(
                "/api/decks?page=0&size=" + invalidSize,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(deckResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test /api/decks/{deckId}/cards with invalid size
        DeckResponse deck = createDeck("InvalidSizeHighDeck" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map<String, Object>> cardResponse = restTemplate.exchange(
                "/api/decks/" + deck.id() + "/cards?page=0&size=" + invalidSize,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(cardResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Property 13: Card Search Filter Correctness ---

    /**
     * Property 13: Card Search Filter Correctness
     * For any non-blank search term q and a Deck containing Cards, the filtered results
     * SHALL only include Cards where front or back contains q as a case-insensitive substring.
     * No Card matching the term SHALL be excluded from results (within the current page).
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 13: Card Search Filter Correctness")
    void cardSearchFilterCorrectness(@ForAll("searchTerms") String searchTerm) {

        // Create a deck
        DeckResponse deck = createDeck("SearchDeck" + UUID.randomUUID().toString().substring(0, 6));

        // Create cards: some matching, some not
        // Cards with searchTerm in front
        createCard(deck.id(), "Hello " + searchTerm + " world", "unrelated content aaa");
        // Cards with searchTerm in back
        createCard(deck.id(), "plain front bbb", "The answer is " + searchTerm.toUpperCase() + " here");
        // Cards with searchTerm in both
        createCard(deck.id(), searchTerm + " question", searchTerm + " answer");
        // Cards that do NOT match
        createCard(deck.id(), "zzzzzzz nomatch front", "zzzzzzz nomatch back");
        createCard(deck.id(), "qqqqqq other front", "qqqqqq other back");

        // Search with the term, use size 100 to get all results in one page
        ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                "/api/decks/" + deck.id() + "/cards?q=" + searchTerm + "&page=0&size=100",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = searchResponse.getBody();
        assertThat(body).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull();

        // Verify every returned card contains the search term (case-insensitive)
        String termLower = searchTerm.toLowerCase();
        for (Map<String, Object> card : content) {
            String front = ((String) card.get("front")).toLowerCase();
            String back = ((String) card.get("back")).toLowerCase();
            assertThat(front.contains(termLower) || back.contains(termLower))
                    .as("Card with front='%s' and back='%s' should contain term '%s'",
                            card.get("front"), card.get("back"), searchTerm)
                    .isTrue();
        }

        // Verify no matching card is excluded: count expected matches
        // We created 3 cards that match and 2 that don't match
        assertThat(content.size()).isGreaterThanOrEqualTo(3);

        // Also verify that the non-matching cards are NOT in results
        for (Map<String, Object> card : content) {
            String front = (String) card.get("front");
            // Our non-matching cards start with "zzzzzzz" or "qqqqqq"
            if (front.startsWith("zzzzzzz") || front.startsWith("qqqqqq")) {
                // If these appear, they must still contain the term
                String frontLower = front.toLowerCase();
                String backLower = ((String) card.get("back")).toLowerCase();
                assertThat(frontLower.contains(termLower) || backLower.contains(termLower)).isTrue();
            }
        }
    }
}
