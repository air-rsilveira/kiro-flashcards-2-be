# Implementation Plan: Flashcards API

## Overview

Implementation of a REST API for flashcards using Java 17+ with Spring Boot 3.x, Spring Data JPA, and H2/PostgreSQL. The plan follows an incremental approach: project setup → data models → CRUD operations → study/review logic → statistics → error handling → property-based tests and integration tests.

## Tasks

- [x] 1. Set up project structure and core infrastructure
  - [x] 1.1 Initialize Spring Boot project with Gradle dependencies
    - Create `build.gradle.kts` with Spring Boot 3.x plugin, Spring Web, Spring Data JPA, H2, PostgreSQL driver, Jakarta Validation, and jqwik test dependencies
    - Create main application class with `@SpringBootApplication`
    - Create `application.yml` with H2 in-memory config for dev/test and PostgreSQL config for production profile
    - _Requirements: 14.5_

  - [x] 1.2 Create JPA entities and enum
    - Implement `Deck` entity with UUID id, name (max 100), description (max 500), createdAt, updatedAt fields and `@PrePersist`/`@PreUpdate` lifecycle callbacks
    - Implement `Card` entity with UUID id, deckId, front (max 5000), back (max 5000), createdAt, updatedAt, and `@ManyToOne` lazy relationship to Deck
    - Implement `Review` entity with UUID id, cardId, result (enum), reviewedAt, nextReviewAt, and `@ManyToOne` lazy relationship to Card
    - Implement `ReviewResult` enum with values: EASY, GOOD, HARD, AGAIN
    - _Requirements: 1.4, 6.6, 12.1_

  - [x] 1.3 Create repository interfaces
    - Implement `DeckRepository` extending `JpaRepository<Deck, UUID>` with `findAllByOrderByCreatedAtDesc` method
    - Implement `CardRepository` extending `JpaRepository<Card, UUID>` with methods for deck-based queries, search, study query, count, and cascade delete
    - Implement `ReviewRepository` extending `JpaRepository<Review, UUID>` with methods for latest review by card, count, bulk find, and cascade delete
    - _Requirements: 2.4, 7.1, 11.3, 11.4_

  - [x] 1.4 Create request/response DTOs
    - Create `CreateDeckRequest` record with `@NotBlank @Size(max=100) name` and `@Size(max=500) description`
    - Create `UpdateDeckRequest` record with same validations
    - Create `CreateCardRequest` record with `@NotBlank @Size(max=5000) front` and `@NotBlank @Size(max=5000) back`
    - Create `UpdateCardRequest` record with same validations
    - Create `CreateReviewRequest` record with `@NotNull ReviewResult result`
    - Create response records: `DeckResponse`, `CardResponse`, `ReviewResponse`, `StatsResponse`, `ResultDistribution`
    - Create error response records: `ValidationErrorResponse`, `ErrorResponse`
    - _Requirements: 1.1, 1.2, 6.1, 12.1, 14.1, 14.2_

- [x] 2. Implement Deck CRUD operations
  - [x] 2.1 Implement DeckService
    - Create `DeckService` class with methods: `createDeck`, `listDecks` (paginated, sorted by createdAt desc), `getDeckById`, `updateDeck`, `deleteDeck`
    - Handle `ResourceNotFoundException` when deck not found
    - Handle optional description (treat null as empty)
    - Implement cascade delete: remove all cards and associated reviews when deleting a deck
    - _Requirements: 1.1, 1.5, 2.1, 2.4, 3.1, 3.2, 4.1, 4.4, 4.5, 5.1, 5.2, 5.3_

  - [x] 2.2 Implement DeckController
    - Create `DeckController` with `@RestController` and base path `/api/decks`
    - Implement `POST /api/decks` → create deck, return 201
    - Implement `GET /api/decks` → list decks paginated (default page=0, size=20, max size=100), return 200
    - Implement `GET /api/decks/{id}` → get deck by ID, return 200
    - Implement `PUT /api/decks/{id}` → update deck, return 200
    - Implement `DELETE /api/decks/{id}` → delete deck, return 204
    - Validate pagination params (page >= 0, 1 <= size <= 100), return 400 if invalid
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.5, 2.6, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 5.1, 5.2, 5.4_

  - [x] 2.3 Write property tests for Deck CRUD
    - **Property 2: Deck Creation Round-Trip** — Create deck with arbitrary valid name/description, fetch by ID, verify fields match
    - **Property 5: Deck Update Preserves CreatedAt** — Update deck, verify createdAt unchanged and updatedAt >= original
    - **Property 17: Listing Order Invariant** — List decks, verify createdAt descending order
    - **Validates: Requirements 1.1, 1.4, 3.1, 4.1, 4.4, 2.4**

  - [x] 2.4 Write unit tests for DeckService
    - Test create deck with valid data
    - Test get deck by non-existent ID throws ResourceNotFoundException
    - Test update deck with non-existent ID throws ResourceNotFoundException
    - Test delete deck cascades to cards and reviews
    - Test list decks with empty database returns empty page
    - _Requirements: 1.1, 2.6, 3.2, 4.2, 5.2, 5.3_

- [x] 3. Implement Card CRUD operations
  - [x] 3.1 Implement CardService
    - Create `CardService` class with methods: `createCard`, `listCards` (paginated with optional search filter q), `getCardById`, `updateCard`, `deleteCard`
    - Handle `ResourceNotFoundException` when card or parent deck not found
    - Implement search filter: case-insensitive substring match on front or back; ignore blank/empty q parameter
    - Implement cascade delete: remove all reviews when deleting a card
    - _Requirements: 6.1, 6.2, 6.6, 7.1, 7.2, 7.3, 7.6, 8.1, 8.2, 9.1, 9.2, 9.4, 10.1, 10.2, 10.4_

  - [x] 3.2 Implement CardController
    - Create `CardController` with endpoints under `/api/decks/{deckId}/cards` and `/api/cards/{id}`
    - Implement `POST /api/decks/{deckId}/cards` → create card, return 201
    - Implement `GET /api/decks/{deckId}/cards` → list cards paginated with optional `?q=` filter, return 200
    - Implement `GET /api/cards/{id}` → get card by ID, return 200
    - Implement `PUT /api/cards/{id}` → update card, return 200
    - Implement `DELETE /api/cards/{id}` → delete card, return 204
    - Validate pagination params (page >= 0, 1 <= size <= 100), return 400 if invalid
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.4, 7.5, 7.7, 8.1, 8.2, 8.3, 9.1, 9.2, 9.3, 10.1, 10.2, 10.3_

  - [x] 3.3 Write property tests for Card CRUD
    - **Property 3: Card Creation Round-Trip** — Create card with arbitrary valid front/back in existing deck, fetch by ID, verify fields match
    - **Property 4: Blank Input Rejection** — Use blank strings for front/back/deck name, verify HTTP 400 and no data persisted
    - **Property 6: Card Update Preserves Immutable Fields** — Update card, verify createdAt and deckId unchanged
    - **Property 7: Cascade Deletion Leaves No Orphans** — Delete deck, verify zero cards and reviews remain
    - **Validates: Requirements 6.1, 6.6, 8.1, 1.2, 6.4, 9.3, 9.1, 9.4, 5.3, 10.1, 10.4**

  - [x] 3.4 Write unit tests for CardService
    - Test create card with non-existent deckId throws ResourceNotFoundException
    - Test search filter is case-insensitive
    - Test blank search term is ignored
    - Test delete card removes associated reviews
    - _Requirements: 6.2, 7.3, 7.6, 10.4_

- [x] 4. Checkpoint - Ensure CRUD operations work
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement Study and Review features
  - [x] 5.1 Implement SpacedRepetitionEngine
    - Create `SpacedRepetitionEngine` `@Component` with method `calculateNextReview(Instant reviewedAt, ReviewResult result)`
    - Implement interval logic: AGAIN → +1 minute, HARD → +10 minutes, GOOD → +1 day, EASY → +4 days
    - _Requirements: 12.4_

  - [x] 5.2 Implement study session logic in CardService
    - Implement method `getCardsForStudy(UUID deckId)` that returns cards due for review
    - Include cards never reviewed (no Review record) and cards with most recent Review's nextReviewAt ≤ now
    - Order: never-reviewed cards first (by createdAt ascending), then due cards (by nextReviewAt ascending)
    - Limit results to maximum 20 cards
    - _Requirements: 11.1, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [x] 5.3 Implement StudyController
    - Create `StudyController` with endpoint `GET /api/decks/{deckId}/study`
    - Return list of CardResponse with status 200
    - Return 404 if deckId not found
    - Return empty list with 200 if no cards are due
    - _Requirements: 11.1, 11.2, 11.7_

  - [x] 5.4 Implement ReviewService
    - Create `ReviewService` with method `registerReview(UUID cardId, ReviewResult result)`
    - Set reviewedAt to current server UTC time
    - Calculate nextReviewAt using SpacedRepetitionEngine
    - Return 404 if cardId not found
    - _Requirements: 12.1, 12.2, 12.4, 12.5_

  - [x] 5.5 Implement ReviewController
    - Create `ReviewController` with endpoint `POST /api/cards/{id}/review`
    - Accept `CreateReviewRequest` with `@Valid` annotation
    - Return ReviewResponse with status 201
    - Return 404 if card not found, 400 if result is invalid/null
    - _Requirements: 12.1, 12.2, 12.3_

  - [x] 5.6 Write property tests for Study and Review
    - **Property 1: Spaced Repetition Interval Calculation** — For any timestamp and ReviewResult, verify exact interval calculation
    - **Property 8: Study Set Correctness** — Verify only never-reviewed or due cards appear in study results
    - **Property 9: Study Set Ordering** — Verify ordering: never-reviewed first (createdAt asc), then due (nextReviewAt asc)
    - **Property 10: Study Set Size Limit** — Verify at most 20 cards returned regardless of deck size
    - **Validates: Requirements 12.4, 11.1, 11.3, 11.4, 11.5, 11.6**

  - [x] 5.7 Write unit tests for SpacedRepetitionEngine and ReviewService
    - Test each ReviewResult interval calculation with known timestamps
    - Test reviewedAt is set to server time
    - Test review with non-existent card throws ResourceNotFoundException
    - _Requirements: 12.4, 12.5, 12.2_

- [x] 6. Implement Statistics
  - [x] 6.1 Implement StatsService
    - Create `StatsService` with method `getDeckStats(UUID deckId)`
    - Calculate totalCards (count of cards in deck)
    - Calculate cardsStudied (cards with at least one review)
    - Calculate cardsDue (cards never reviewed OR with nextReviewAt ≤ now)
    - Calculate totalReviews (total review count for deck's cards)
    - Calculate resultDistribution (count per ReviewResult: EASY, GOOD, HARD, AGAIN)
    - Calculate studyStreak (consecutive days with at least one review counting backwards from today; 0 if no review today)
    - Return all zeros if deck has no cards
    - _Requirements: 13.1, 13.3, 13.4, 13.5_

  - [x] 6.2 Implement StatsController
    - Create `StatsController` with endpoint `GET /api/decks/{deckId}/stats`
    - Return StatsResponse with status 200
    - Return 404 if deckId not found
    - _Requirements: 13.1, 13.2_

  - [x] 6.3 Write property tests for Statistics
    - **Property 14: Stats Computation Correctness** — For any deck with known cards/reviews, verify all stats fields are correct
    - **Property 15: Study Streak Calculation** — For any sequence of review timestamps, verify streak equals consecutive days backwards from today
    - **Validates: Requirements 13.1, 13.3, 13.4**

  - [x] 6.4 Write unit tests for StatsService
    - Test stats for deck with no cards returns all zeros
    - Test studyStreak is 0 when no reviews today
    - Test studyStreak counts consecutive days correctly
    - Test resultDistribution counts each ReviewResult type
    - _Requirements: 13.3, 13.4, 13.5_

- [x] 7. Implement Global Error Handling
  - [x] 7.1 Implement GlobalExceptionHandler
    - Create `@RestControllerAdvice` class `GlobalExceptionHandler`
    - Handle `MethodArgumentNotValidException` → 400 with `ValidationErrorResponse` (field: message format)
    - Handle `ConstraintViolationException` → 400 with `ValidationErrorResponse`
    - Handle `InvalidFormatException` (invalid enum) → 400 with `ValidationErrorResponse`
    - Handle `MethodArgumentTypeMismatchException` (invalid UUID) → 400 with `ErrorResponse`
    - Handle `ResourceNotFoundException` → 404 with `ErrorResponse` (resource name and ID)
    - Handle `HttpRequestMethodNotSupportedException` → 405 with `ErrorResponse` (allowed methods)
    - Handle generic `Exception` → 500 with `ErrorResponse` (fixed message, no internal details)
    - All error responses use Content-Type `application/json`
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_

  - [x] 7.2 Create ResourceNotFoundException custom exception
    - Create `ResourceNotFoundException` extending `RuntimeException` with fields: resourceName, fieldName, fieldValue
    - _Requirements: 14.2_

  - [x] 7.3 Write property tests for Error Handling
    - **Property 16: Error Response Structure Consistency** — For any error (400, 404, 405, 500), verify response has application/json content-type and contains timestamp, status, and error fields
    - **Validates: Requirements 14.1, 14.2, 14.5**

  - [x] 7.4 Write unit tests for GlobalExceptionHandler
    - Test validation error produces 400 with messages list
    - Test ResourceNotFoundException produces 404 with resource details
    - Test generic exception produces 500 without internal details
    - Test unsupported method produces 405 with allowed methods
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

- [x] 8. Implement Pagination validation and search
  - [x] 8.1 Implement pagination parameter validation
    - Add validation logic for pagination parameters across all listing endpoints (page >= 0, 1 <= size <= 100)
    - Ensure default values page=0, size=20 when not provided
    - Return 400 with appropriate error message for invalid pagination params
    - _Requirements: 2.2, 2.3, 2.5, 7.4, 7.5, 7.7_

  - [x] 8.2 Write property tests for Pagination
    - **Property 11: Pagination Metadata Consistency** — For N items, page P, size S, verify totalElements, totalPages, number, size, and content.length are correct
    - **Property 12: Invalid Pagination Rejection** — For page < 0, size < 1, or size > 100, verify HTTP 400
    - **Property 13: Card Search Filter Correctness** — For any non-blank term, verify only matching cards returned (case-insensitive substring)
    - **Validates: Requirements 2.1, 2.2, 2.5, 7.1, 7.4, 7.7, 7.3**

- [x] 9. Integration tests and final verification
  - [x] 9.1 Write integration tests for Deck endpoints
    - Test full CRUD lifecycle with `@SpringBootTest` and `TestRestTemplate`/`MockMvc`
    - Test pagination with multiple decks
    - Test cascade delete removes cards and reviews
    - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 5.3_

  - [x] 9.2 Write integration tests for Card endpoints
    - Test full CRUD lifecycle for cards within a deck
    - Test search filter with various terms
    - Test card creation with non-existent deck returns 404
    - _Requirements: 6.1, 7.1, 7.3, 8.1, 9.1, 10.1_

  - [x] 9.3 Write integration tests for Study and Review endpoints
    - Test study endpoint returns correct cards based on review state
    - Test review registration calculates correct nextReviewAt
    - Test study ordering (never-reviewed first, then by nextReviewAt)
    - _Requirements: 11.1, 11.3, 11.6, 12.1, 12.4_

  - [x] 9.4 Write integration tests for Stats endpoint
    - Test stats with deck containing various review states
    - Test studyStreak calculation
    - Test empty deck returns all zeros
    - _Requirements: 13.1, 13.3, 13.4, 13.5_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik
- Unit tests validate specific examples and edge cases
- Integration tests use `@SpringBootTest` with H2 in-memory database
- The `ResourceNotFoundException` (task 7.2) is used throughout the service layer; if implementing sequentially, create it early or as part of task 2.1

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.4"] },
    { "id": 2, "tasks": ["1.3", "7.2"] },
    { "id": 3, "tasks": ["2.1", "3.1", "5.1"] },
    { "id": 4, "tasks": ["2.2", "3.2", "5.2", "5.4", "6.1"] },
    { "id": 5, "tasks": ["5.3", "5.5", "6.2", "7.1", "8.1"] },
    { "id": 6, "tasks": ["2.3", "2.4", "3.3", "3.4", "5.6", "5.7", "6.3", "6.4", "7.3", "7.4", "8.2"] },
    { "id": 7, "tasks": ["9.1", "9.2", "9.3", "9.4"] }
  ]
}
```
