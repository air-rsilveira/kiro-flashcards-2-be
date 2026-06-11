# Project Structure

## Package: `com.flashcards`

```
src/main/java/com/flashcards/
├── FlashcardsApplication.java       # Spring Boot entry point
├── controller/                      # REST controllers (@RestController)
│   ├── CardController.java
│   ├── DeckController.java
│   ├── ReviewController.java
│   ├── StatsController.java
│   └── StudyController.java
├── dto/                             # Request/response records
│   ├── Create*Request.java          # POST payloads (validated)
│   ├── Update*Request.java          # PUT payloads (validated)
│   ├── *Response.java               # API responses
│   ├── ErrorResponse.java           # Standard error format
│   └── ValidationErrorResponse.java # Validation error format
├── entity/                          # JPA entities
│   ├── Card.java
│   ├── Deck.java
│   ├── Review.java
│   └── ReviewResult.java            # Enum: EASY, GOOD, HARD, AGAIN
├── exception/                       # Error handling
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│   └── ResourceNotFoundException.java
├── repository/                      # Spring Data JPA repositories
│   ├── CardRepository.java
│   ├── DeckRepository.java
│   └── ReviewRepository.java
└── service/                         # Business logic
    ├── CardService.java
    ├── DeckService.java
    ├── ReviewService.java
    ├── SpacedRepetitionEngine.java  # Interval calculation
    └── StatsService.java
```

## Test Structure

```
src/test/java/com/flashcards/
├── integration/    # Full Spring context, TestRestTemplate, end-to-end flows
├── property/       # jqwik property-based tests (@JqwikSpringSupport)
├── service/        # Unit tests for isolated services
└── unit/           # Unit tests for services with mocked dependencies
```

## Architecture Conventions

- **Layered architecture**: Controller → Service → Repository
- **DTOs as Java records**: Immutable request/response objects
- **Constructor injection**: No `@Autowired` on fields; explicit constructor DI
- **Entities use JPA lifecycle callbacks**: `@PrePersist` / `@PreUpdate` for timestamps
- **UUIDs as identifiers**: Generated via `GenerationType.UUID`
- **Manual mapping**: Entity-to-DTO conversion via private `toResponse()` methods in services (no MapStruct)
- **Validation at controller layer**: `@Valid` on request bodies
- **Pagination via Spring `Pageable`**: Default page size 20, max 100
- **Error responses**: Consistent JSON format via `GlobalExceptionHandler` for 400/404/405/500
- **Cascade deletes**: Handled manually in service layer (reviews → cards → deck)
