# Tech Stack

## Runtime & Language

- Java 21
- Spring Boot 3.3.5

## Frameworks & Libraries

- **Web**: Spring Web (REST controllers)
- **Persistence**: Spring Data JPA with Hibernate
- **Validation**: Jakarta Validation (`@Valid`, `@NotBlank`, `@Size`)
- **Database**: H2 (dev/test), PostgreSQL (production via `production` profile)
- **Testing**: JUnit 5 + jqwik 1.8.5 (property-based testing) + jqwik-spring 0.12.0

## Build System

- Gradle with Kotlin DSL (`build.gradle.kts`)
- Spring Dependency Management plugin 1.1.6

## Common Commands

```bash
# Run the application (H2 in-memory, port 8080)
./gradlew bootRun

# Run all tests (JUnit 5 + jqwik, ~76 tests)
./gradlew test

# Build the project
./gradlew build

# Run with Docker (PostgreSQL)
docker compose up --build

# Clean build artifacts
./gradlew clean
```

## Test Engines

Both `junit-jupiter` and `jqwik` engines are registered in the Gradle config. Tests use H2 in-memory with `SpringBootTest.WebEnvironment.RANDOM_PORT` and `TestRestTemplate`.

## Profiles

- **default**: H2 in-memory, `ddl-auto: create-drop`, H2 console enabled
- **production**: PostgreSQL, `ddl-auto: validate`, H2 console disabled
