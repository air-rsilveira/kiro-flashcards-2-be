package com.flashcards.property;

import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Error Handling.
 * 
 * Property 16: Error Response Structure Consistency
 * For any error response (400, 404, 405, 500), the response SHALL have
 * Content-Type application/json and contain at minimum the fields:
 * timestamp (ISO 8601 format), status (numeric HTTP code), and error (description string).
 *
 * Validates: Requirements 14.1, 14.2, 14.5
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingPropertyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- Error Scenario Enum ---

    enum ErrorScenario {
        // 400 errors
        POST_DECK_BLANK_NAME,
        POST_DECK_MISSING_NAME,
        GET_DECK_INVALID_UUID,
        // 404 errors
        GET_DECK_NOT_FOUND,
        GET_CARD_NOT_FOUND,
        // 405 errors
        DELETE_DECKS_COLLECTION,
        PUT_DECKS_COLLECTION
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<ErrorScenario> errorScenarios() {
        return Arbitraries.of(ErrorScenario.values());
    }

    // --- Property 16: Error Response Structure Consistency ---

    /**
     * Property 16: Error Response Structure Consistency
     * For any error (400, 404, 405, 500), verify response has application/json
     * content-type and contains timestamp, status, and error fields.
     *
     * Validates: Requirements 14.1, 14.2, 14.5
     */
    @Property(tries = 100)
    @Tag("Feature: flashcards-api, Property 16: Error Response Structure Consistency")
    void errorResponseStructureConsistency(@ForAll("errorScenarios") ErrorScenario scenario) {
        ResponseEntity<Map> response = executeErrorScenario(scenario);

        int expectedStatus = getExpectedStatus(scenario);

        // Verify Content-Type contains application/json
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();

        // Verify HTTP status code matches expected
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);

        // Verify response body contains required fields
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // Verify "timestamp" field exists and is parseable as ISO 8601 Instant
        assertThat(body).containsKey("timestamp");
        Object timestamp = body.get("timestamp");
        assertThat(timestamp).isNotNull();
        // Verify it's a valid ISO 8601 timestamp (parseable as Instant)
        Instant parsedTimestamp = Instant.parse(timestamp.toString());
        assertThat(parsedTimestamp).isNotNull();
        assertThat(parsedTimestamp).isBefore(Instant.now().plusSeconds(5));

        // Verify "status" field exists and is numeric matching the HTTP status code
        assertThat(body).containsKey("status");
        Object status = body.get("status");
        assertThat(status).isNotNull();
        assertThat(((Number) status).intValue()).isEqualTo(expectedStatus);

        // Verify "error" field exists and is a non-empty string
        assertThat(body).containsKey("error");
        Object error = body.get("error");
        assertThat(error).isNotNull();
        assertThat(error.toString()).isNotBlank();
    }

    // --- Helper Methods ---

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> executeErrorScenario(ErrorScenario scenario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        switch (scenario) {
            case POST_DECK_BLANK_NAME: {
                // POST /api/decks with blank name → 400
                String body = "{\"name\": \"   \", \"description\": \"test\"}";
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                return restTemplate.exchange("/api/decks", HttpMethod.POST, entity, Map.class);
            }
            case POST_DECK_MISSING_NAME: {
                // POST /api/decks with missing name → 400
                String body = "{\"description\": \"test\"}";
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                return restTemplate.exchange("/api/decks", HttpMethod.POST, entity, Map.class);
            }
            case GET_DECK_INVALID_UUID: {
                // GET /api/decks/not-a-uuid → 400
                return restTemplate.exchange(
                        "/api/decks/not-a-uuid", HttpMethod.GET, null, Map.class);
            }
            case GET_DECK_NOT_FOUND: {
                // GET /api/decks/{random-uuid} → 404
                UUID randomId = UUID.randomUUID();
                return restTemplate.exchange(
                        "/api/decks/{id}", HttpMethod.GET, null, Map.class, randomId);
            }
            case GET_CARD_NOT_FOUND: {
                // GET /api/cards/{random-uuid} → 404
                UUID randomId = UUID.randomUUID();
                return restTemplate.exchange(
                        "/api/cards/{id}", HttpMethod.GET, null, Map.class, randomId);
            }
            case DELETE_DECKS_COLLECTION: {
                // DELETE /api/decks (collection, not supported) → 405
                return restTemplate.exchange(
                        "/api/decks", HttpMethod.DELETE, null, Map.class);
            }
            case PUT_DECKS_COLLECTION: {
                // PUT /api/decks (collection, not supported) → 405
                HttpEntity<String> entity = new HttpEntity<>("{}", headers);
                return restTemplate.exchange(
                        "/api/decks", HttpMethod.PUT, entity, Map.class);
            }
            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private int getExpectedStatus(ErrorScenario scenario) {
        switch (scenario) {
            case POST_DECK_BLANK_NAME:
            case POST_DECK_MISSING_NAME:
            case GET_DECK_INVALID_UUID:
                return 400;
            case GET_DECK_NOT_FOUND:
            case GET_CARD_NOT_FOUND:
                return 404;
            case DELETE_DECKS_COLLECTION:
            case PUT_DECKS_COLLECTION:
                return 405;
            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }
}
