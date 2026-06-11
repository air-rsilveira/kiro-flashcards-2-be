package com.flashcards.property;

import com.flashcards.dto.CreateDeckRequest;
import com.flashcards.dto.DeckResponse;
import com.flashcards.dto.UpdateDeckRequest;
import com.flashcards.repository.DeckRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Property-based tests for Deck CRUD operations.
 * Validates: Requirements 1.1, 1.4, 3.1, 4.1, 4.4, 2.4
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeckCrudPropertyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeckRepository deckRepository;

    @AfterProperty
    void cleanup() {
        deckRepository.deleteAll();
        deckRepository.flush();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> validDeckNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_', '.', ',')
                .ofMinLength(0)
                .ofMaxLength(500);
    }

    // --- Property 2: Deck Creation Round-Trip ---

    /**
     * Property 2: Deck Creation Round-Trip
     * For any valid deck name and optional description, creating a Deck via POST
     * and then fetching it by the returned ID SHALL return a Deck with the same
     * name and description, plus valid auto-generated id, createdAt, and updatedAt.
     *
     * Validates: Requirements 1.1, 1.4, 3.1
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 2: Deck Creation Round-Trip")
    void deckCreationRoundTrip(
            @ForAll("validDeckNames") String name,
            @ForAll("validDescriptions") String description) {

        // Create deck
        CreateDeckRequest createRequest = new CreateDeckRequest(name, description);
        ResponseEntity<DeckResponse> createResponse = restTemplate.postForEntity(
                "/api/decks", createRequest, DeckResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DeckResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        // Fetch by ID
        ResponseEntity<DeckResponse> getResponse = restTemplate.getForEntity(
                "/api/decks/{id}", DeckResponse.class, created.id());

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeckResponse fetched = getResponse.getBody();
        assertThat(fetched).isNotNull();

        // Verify fields match
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo(name);
        // Description: null is treated as empty string by the service
        String expectedDescription = description == null ? "" : description;
        assertThat(fetched.description()).isEqualTo(expectedDescription);
        assertThat(fetched.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS));
        assertThat(fetched.updatedAt()).isCloseTo(created.updatedAt(), within(1, ChronoUnit.MICROS));
    }

    // --- Property 5: Deck Update Preserves CreatedAt ---

    /**
     * Property 5: Deck Update Preserves CreatedAt
     * For any existing Deck and any valid update payload, updating the Deck SHALL
     * result in the same createdAt value, an updatedAt >= the original, and the
     * name/description reflecting the new values.
     *
     * Validates: Requirements 4.1, 4.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 5: Deck Update Preserves CreatedAt")
    void deckUpdatePreservesCreatedAt(
            @ForAll("validDeckNames") String originalName,
            @ForAll("validDescriptions") String originalDescription,
            @ForAll("validDeckNames") String newName,
            @ForAll("validDescriptions") String newDescription) {

        // Create a deck first
        CreateDeckRequest createRequest = new CreateDeckRequest(originalName, originalDescription);
        ResponseEntity<DeckResponse> createResponse = restTemplate.postForEntity(
                "/api/decks", createRequest, DeckResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DeckResponse created = createResponse.getBody();
        assertThat(created).isNotNull();

        Instant originalCreatedAt = created.createdAt();
        Instant originalUpdatedAt = created.updatedAt();

        // Update the deck
        UpdateDeckRequest updateRequest = new UpdateDeckRequest(newName, newDescription);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateDeckRequest> entity = new HttpEntity<>(updateRequest, headers);

        ResponseEntity<DeckResponse> updateResponse = restTemplate.exchange(
                "/api/decks/{id}", HttpMethod.PUT, entity, DeckResponse.class, created.id());

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeckResponse updated = updateResponse.getBody();
        assertThat(updated).isNotNull();

        // Verify createdAt is unchanged
        assertThat(updated.createdAt()).isCloseTo(originalCreatedAt, within(1, ChronoUnit.MICROS));

        // Verify updatedAt >= original updatedAt
        assertThat(updated.updatedAt()).isAfterOrEqualTo(originalUpdatedAt.truncatedTo(ChronoUnit.MILLIS));

        // Verify name and description reflect new values
        assertThat(updated.name()).isEqualTo(newName);
        String expectedDescription = newDescription == null ? "" : newDescription;
        assertThat(updated.description()).isEqualTo(expectedDescription);
    }

    // --- Property 17: Listing Order Invariant ---

    /**
     * Property 17: Listing Order Invariant
     * For any paginated deck listing, the results SHALL be ordered by createdAt
     * descending — for any two consecutive items, the first item's createdAt
     * SHALL be >= the second item's createdAt.
     *
     * Validates: Requirements 2.4
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 17: Listing Order Invariant")
    void listingOrderInvariant(@ForAll("deckCounts") int deckCount) {

        // Create multiple decks
        for (int i = 0; i < deckCount; i++) {
            CreateDeckRequest request = new CreateDeckRequest("Deck " + i, "Desc " + i);
            ResponseEntity<DeckResponse> response = restTemplate.postForEntity(
                    "/api/decks", request, DeckResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // List all decks
        ResponseEntity<Map<String, Object>> listResponse = restTemplate.exchange(
                "/api/decks?page=0&size=100",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = listResponse.getBody();
        assertThat(body).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).isNotNull();
        assertThat(content).hasSizeGreaterThanOrEqualTo(deckCount);

        // Verify createdAt descending order
        for (int i = 0; i < content.size() - 1; i++) {
            Instant current = Instant.parse((String) content.get(i).get("createdAt"));
            Instant next = Instant.parse((String) content.get(i + 1).get("createdAt"));
            assertThat(current).isAfterOrEqualTo(next);
        }
    }

    @Provide
    Arbitrary<Integer> deckCounts() {
        return Arbitraries.integers().between(2, 5);
    }
}
