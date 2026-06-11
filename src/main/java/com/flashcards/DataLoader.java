package com.flashcards;

import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("!production")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ReviewRepository reviewRepository;

    public DataLoader(DeckRepository deckRepository,
                      CardRepository cardRepository,
                      ReviewRepository reviewRepository) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    public void run(String... args) {
        if (deckRepository.count() > 0) {
            log.info("Database already contains data, skipping mock data loading.");
            return;
        }

        log.info("Loading mock data for development...");

        Deck javaBasics = createDeck("Java Basics", "Fundamental Java concepts and syntax");
        Deck springBoot = createDeck("Spring Boot", "Spring Boot framework essentials");
        Deck designPatterns = createDeck("Design Patterns", "Common software design patterns");

        // Java Basics cards
        Card javaCard1 = createCard(javaBasics, "What is the difference between == and .equals() in Java?",
                "== compares references (memory addresses), while .equals() compares the actual content/value of objects.");
        Card javaCard2 = createCard(javaBasics, "What is a Java record?",
                "A record is a special class declaration (Java 14+) that provides a compact syntax for immutable data carriers with auto-generated equals(), hashCode(), and toString().");
        Card javaCard3 = createCard(javaBasics, "What is the difference between an interface and an abstract class?",
                "An interface defines a contract with default/static methods (no state), while an abstract class can have state (fields), constructors, and partially implemented behavior.");
        Card javaCard4 = createCard(javaBasics, "What are sealed classes in Java?",
                "Sealed classes (Java 17) restrict which classes can extend them using the 'permits' keyword, enabling exhaustive pattern matching.");
        Card javaCard5 = createCard(javaBasics, "What is the difference between checked and unchecked exceptions?",
                "Checked exceptions must be declared or caught at compile time (e.g., IOException). Unchecked exceptions (RuntimeException subclasses) don't require explicit handling.");

        // Spring Boot cards
        Card springCard1 = createCard(springBoot, "What does @SpringBootApplication do?",
                "It combines @Configuration, @EnableAutoConfiguration, and @ComponentScan. It marks the main class and triggers Spring Boot auto-configuration.");
        Card springCard2 = createCard(springBoot, "What is dependency injection in Spring?",
                "DI is a design pattern where Spring container manages object creation and injects dependencies via constructor, setter, or field injection. Constructor injection is preferred.");
        Card springCard3 = createCard(springBoot, "What is the difference between @Component, @Service, and @Repository?",
                "@Component is a generic stereotype. @Service indicates business logic (no extra behavior). @Repository indicates data access and adds automatic exception translation.");
        Card springCard4 = createCard(springBoot, "What is Spring Boot auto-configuration?",
                "Auto-configuration automatically configures beans based on classpath dependencies and properties. For example, adding H2 dependency auto-configures an in-memory datasource.");
        Card springCard5 = createCard(springBoot, "What is the purpose of application.yml/properties?",
                "It externalizes configuration (database URLs, server port, etc.) from code. Spring Boot reads it from src/main/resources and supports profile-specific overrides.");

        // Design Patterns cards
        Card patternCard1 = createCard(designPatterns, "What is the Singleton pattern?",
                "Ensures a class has only one instance and provides a global access point. In Spring, beans are singletons by default within the application context.");
        Card patternCard2 = createCard(designPatterns, "What is the Strategy pattern?",
                "Defines a family of algorithms, encapsulates each one, and makes them interchangeable. The client can choose the algorithm at runtime without modifying context code.");
        Card patternCard3 = createCard(designPatterns, "What is the Observer pattern?",
                "Defines a one-to-many dependency between objects. When one object (subject) changes state, all dependents (observers) are notified. Used in event-driven systems.");
        Card patternCard4 = createCard(designPatterns, "What is the Builder pattern?",
                "Separates the construction of a complex object from its representation, allowing the same construction process to create different representations step by step.");

        // Add some reviews to simulate study history
        Instant now = Instant.now();
        createReview(javaCard1, ReviewResult.GOOD, now.minus(2, ChronoUnit.DAYS));
        createReview(javaCard2, ReviewResult.EASY, now.minus(1, ChronoUnit.DAYS));
        createReview(javaCard3, ReviewResult.HARD, now.minus(3, ChronoUnit.HOURS));
        createReview(springCard1, ReviewResult.GOOD, now.minus(1, ChronoUnit.DAYS));
        createReview(springCard2, ReviewResult.AGAIN, now.minus(30, ChronoUnit.MINUTES));
        createReview(patternCard1, ReviewResult.EASY, now.minus(3, ChronoUnit.DAYS));

        log.info("Mock data loaded: 3 decks, 14 cards, 6 reviews.");
    }

    private Deck createDeck(String name, String description) {
        Deck deck = new Deck();
        deck.setName(name);
        deck.setDescription(description);
        return deckRepository.save(deck);
    }

    private Card createCard(Deck deck, String front, String back) {
        Card card = new Card();
        card.setDeckId(deck.getId());
        card.setFront(front);
        card.setBack(back);
        return cardRepository.save(card);
    }

    private void createReview(Card card, ReviewResult result, Instant reviewedAt) {
        Review review = new Review();
        review.setCardId(card.getId());
        review.setResult(result);
        review.setReviewedAt(reviewedAt);
        review.setNextReviewAt(calculateNextReview(reviewedAt, result));
        reviewRepository.save(review);
    }

    private Instant calculateNextReview(Instant reviewedAt, ReviewResult result) {
        return switch (result) {
            case AGAIN -> reviewedAt.plus(1, ChronoUnit.MINUTES);
            case HARD -> reviewedAt.plus(10, ChronoUnit.MINUTES);
            case GOOD -> reviewedAt.plus(1, ChronoUnit.DAYS);
            case EASY -> reviewedAt.plus(4, ChronoUnit.DAYS);
        };
    }
}
