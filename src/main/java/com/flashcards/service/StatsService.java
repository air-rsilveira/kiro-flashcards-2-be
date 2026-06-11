package com.flashcards.service;

import com.flashcards.dto.ResultDistribution;
import com.flashcards.dto.StatsResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ReviewRepository reviewRepository;

    public StatsService(DeckRepository deckRepository,
                        CardRepository cardRepository,
                        ReviewRepository reviewRepository) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse getDeckStats(UUID deckId) {
        deckRepository.findById(deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", "id", deckId));

        int totalCards = cardRepository.countByDeckId(deckId);

        if (totalCards == 0) {
            return new StatsResponse(0, 0, 0, 0L, 0, new ResultDistribution(0, 0, 0, 0));
        }

        List<Card> cards = cardRepository.findByDeckId(deckId);
        List<UUID> cardIds = cards.stream().map(Card::getId).toList();

        List<Review> reviews = reviewRepository.findByCardIdIn(cardIds);
        long totalReviews = reviews.size();

        // cardsStudied: distinct card IDs that have at least one review
        long cardsStudied = reviews.stream()
                .map(Review::getCardId)
                .distinct()
                .count();

        // cardsDue: cards never reviewed OR latest review's nextReviewAt <= now
        Instant now = Instant.now();
        Map<UUID, Review> latestReviewByCard = reviews.stream()
                .collect(Collectors.toMap(
                        Review::getCardId,
                        r -> r,
                        (r1, r2) -> r1.getReviewedAt().isAfter(r2.getReviewedAt()) ? r1 : r2
                ));

        long cardsDue = cards.stream()
                .filter(card -> {
                    Review latestReview = latestReviewByCard.get(card.getId());
                    // Never reviewed OR nextReviewAt <= now
                    return latestReview == null || !latestReview.getNextReviewAt().isAfter(now);
                })
                .count();

        // resultDistribution: count per ReviewResult
        Map<ReviewResult, Long> resultCounts = reviews.stream()
                .collect(Collectors.groupingBy(Review::getResult, Collectors.counting()));

        ResultDistribution resultDistribution = new ResultDistribution(
                resultCounts.getOrDefault(ReviewResult.EASY, 0L),
                resultCounts.getOrDefault(ReviewResult.GOOD, 0L),
                resultCounts.getOrDefault(ReviewResult.HARD, 0L),
                resultCounts.getOrDefault(ReviewResult.AGAIN, 0L)
        );

        // studyStreak: consecutive days with at least one review counting backwards from today
        int studyStreak = calculateStudyStreak(reviews);

        return new StatsResponse(
                totalCards,
                (int) cardsStudied,
                (int) cardsDue,
                totalReviews,
                studyStreak,
                resultDistribution
        );
    }

    private int calculateStudyStreak(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Get distinct review dates (in UTC)
        java.util.Set<LocalDate> reviewDates = reviews.stream()
                .map(r -> r.getReviewedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());

        // If today has no review, streak is 0
        if (!reviewDates.contains(today)) {
            return 0;
        }

        // Count consecutive days backwards from today
        int streak = 0;
        LocalDate date = today;
        while (reviewDates.contains(date)) {
            streak++;
            date = date.minusDays(1);
        }

        return streak;
    }
}
