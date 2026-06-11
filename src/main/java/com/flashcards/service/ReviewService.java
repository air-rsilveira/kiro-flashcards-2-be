package com.flashcards.service;

import com.flashcards.dto.ReviewResponse;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReviewService {

    private final CardRepository cardRepository;
    private final ReviewRepository reviewRepository;
    private final SpacedRepetitionEngine spacedRepetitionEngine;

    public ReviewService(CardRepository cardRepository,
                         ReviewRepository reviewRepository,
                         SpacedRepetitionEngine spacedRepetitionEngine) {
        this.cardRepository = cardRepository;
        this.reviewRepository = reviewRepository;
        this.spacedRepetitionEngine = spacedRepetitionEngine;
    }

    @Transactional
    public ReviewResponse registerReview(UUID cardId, ReviewResult result) {
        cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", cardId));

        Instant reviewedAt = Instant.now();
        Instant nextReviewAt = spacedRepetitionEngine.calculateNextReview(reviewedAt, result);

        Review review = new Review();
        review.setCardId(cardId);
        review.setResult(result);
        review.setReviewedAt(reviewedAt);
        review.setNextReviewAt(nextReviewAt);

        Review saved = reviewRepository.save(review);
        return toResponse(saved);
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getCardId(),
                review.getResult(),
                review.getReviewedAt(),
                review.getNextReviewAt()
        );
    }
}
