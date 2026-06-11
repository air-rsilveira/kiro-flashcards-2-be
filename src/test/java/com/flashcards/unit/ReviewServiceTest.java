package com.flashcards.unit;

import com.flashcards.dto.ReviewResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.ReviewRepository;
import com.flashcards.service.ReviewService;
import com.flashcards.service.SpacedRepetitionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService.
 * Validates: Requirements 12.4, 12.5, 12.2
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private SpacedRepetitionEngine spacedRepetitionEngine;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void registerReview_withNonExistentCard_throwsResourceNotFoundException() {
        // Validates: Requirement 12.2
        UUID nonExistentCardId = UUID.randomUUID();
        when(cardRepository.findById(nonExistentCardId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reviewService.registerReview(nonExistentCardId, ReviewResult.GOOD)
        );

        assertTrue(exception.getMessage().contains("Card"));
        assertTrue(exception.getMessage().contains(nonExistentCardId.toString()));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void registerReview_success_setsReviewedAtToApproximatelyNow() {
        // Validates: Requirement 12.5
        UUID cardId = UUID.randomUUID();
        Card card = new Card();
        card.setId(cardId);

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(spacedRepetitionEngine.calculateNextReview(any(Instant.class), eq(ReviewResult.GOOD)))
                .thenAnswer(invocation -> {
                    Instant reviewedAt = invocation.getArgument(0);
                    return reviewedAt.plus(1, ChronoUnit.DAYS);
                });
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });

        Instant before = Instant.now();
        ReviewResponse response = reviewService.registerReview(cardId, ReviewResult.GOOD);
        Instant after = Instant.now();

        assertNotNull(response.reviewedAt());
        // reviewedAt should be between before and after (within a few seconds of now)
        assertTrue(
                !response.reviewedAt().isBefore(before.minus(Duration.ofSeconds(2))),
                "reviewedAt should not be before the test start time"
        );
        assertTrue(
                !response.reviewedAt().isAfter(after.plus(Duration.ofSeconds(2))),
                "reviewedAt should not be after the test end time"
        );
    }

    @Test
    void registerReview_success_calculatesNextReviewAtViaEngine() {
        // Validates: Requirement 12.4
        UUID cardId = UUID.randomUUID();
        Card card = new Card();
        card.setId(cardId);

        Instant expectedNextReview = Instant.parse("2024-06-15T10:01:00Z");

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(spacedRepetitionEngine.calculateNextReview(any(Instant.class), eq(ReviewResult.AGAIN)))
                .thenReturn(expectedNextReview);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });

        ReviewResponse response = reviewService.registerReview(cardId, ReviewResult.AGAIN);

        assertEquals(expectedNextReview, response.nextReviewAt());
        verify(spacedRepetitionEngine).calculateNextReview(any(Instant.class), eq(ReviewResult.AGAIN));
    }

    @Test
    void registerReview_success_savesReviewWithCorrectFields() {
        // Validates: Requirements 12.4, 12.5
        UUID cardId = UUID.randomUUID();
        Card card = new Card();
        card.setId(cardId);

        Instant fixedNextReview = Instant.parse("2024-06-15T10:10:00Z");

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(spacedRepetitionEngine.calculateNextReview(any(Instant.class), eq(ReviewResult.HARD)))
                .thenReturn(fixedNextReview);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });

        reviewService.registerReview(cardId, ReviewResult.HARD);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());

        Review savedReview = captor.getValue();
        assertEquals(cardId, savedReview.getCardId());
        assertEquals(ReviewResult.HARD, savedReview.getResult());
        assertNotNull(savedReview.getReviewedAt());
        assertEquals(fixedNextReview, savedReview.getNextReviewAt());
    }
}
