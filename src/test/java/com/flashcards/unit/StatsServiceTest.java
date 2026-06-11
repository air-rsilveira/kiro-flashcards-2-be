package com.flashcards.unit;

import com.flashcards.dto.StatsResponse;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.entity.Review;
import com.flashcards.entity.ReviewResult;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import com.flashcards.service.StatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getDeckStats_withNoCards_returnsAllZeros() {
        // Given
        UUID deckId = UUID.randomUUID();
        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Empty Deck");

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeckId(deckId)).thenReturn(0);

        // When
        StatsResponse stats = statsService.getDeckStats(deckId);

        // Then
        assertThat(stats.totalCards()).isZero();
        assertThat(stats.cardsStudied()).isZero();
        assertThat(stats.cardsDue()).isZero();
        assertThat(stats.totalReviews()).isZero();
        assertThat(stats.studyStreak()).isZero();
        assertThat(stats.resultDistribution().easy()).isZero();
        assertThat(stats.resultDistribution().good()).isZero();
        assertThat(stats.resultDistribution().hard()).isZero();
        assertThat(stats.resultDistribution().again()).isZero();
    }

    @Test
    void getDeckStats_studyStreakIsZero_whenNoReviewsToday() {
        // Given
        UUID deckId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();

        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Test Deck");

        Card card = new Card();
        card.setId(cardId);
        card.setDeckId(deckId);

        // Reviews all happened yesterday - no reviews today
        Instant yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);

        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setCardId(cardId);
        review.setResult(ReviewResult.GOOD);
        review.setReviewedAt(yesterday);
        review.setNextReviewAt(yesterday.plusSeconds(86400));

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeckId(deckId)).thenReturn(1);
        when(cardRepository.findByDeckId(deckId)).thenReturn(List.of(card));
        when(reviewRepository.findByCardIdIn(List.of(cardId))).thenReturn(List.of(review));

        // When
        StatsResponse stats = statsService.getDeckStats(deckId);

        // Then
        assertThat(stats.studyStreak()).isZero();
    }

    @Test
    void getDeckStats_studyStreakCountsConsecutiveDays() {
        // Given
        UUID deckId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();

        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Test Deck");

        Card card = new Card();
        card.setId(cardId);
        card.setDeckId(deckId);

        // Reviews for today, yesterday, and day before → streak should be 3
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayReviewTime = today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Instant yesterdayReviewTime = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Instant twoDaysAgoReviewTime = today.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);

        Review reviewToday = createReview(cardId, ReviewResult.EASY, todayReviewTime);
        Review reviewYesterday = createReview(cardId, ReviewResult.GOOD, yesterdayReviewTime);
        Review reviewTwoDaysAgo = createReview(cardId, ReviewResult.HARD, twoDaysAgoReviewTime);

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeckId(deckId)).thenReturn(1);
        when(cardRepository.findByDeckId(deckId)).thenReturn(List.of(card));
        when(reviewRepository.findByCardIdIn(List.of(cardId)))
                .thenReturn(List.of(reviewToday, reviewYesterday, reviewTwoDaysAgo));

        // When
        StatsResponse stats = statsService.getDeckStats(deckId);

        // Then
        assertThat(stats.studyStreak()).isEqualTo(3);
    }

    @Test
    void getDeckStats_studyStreakBreaksOnGap() {
        // Given
        UUID deckId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();

        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Test Deck");

        Card card = new Card();
        card.setId(cardId);
        card.setDeckId(deckId);

        // Reviews for today and 2 days ago but NOT yesterday → streak should be 1
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayReviewTime = today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Instant twoDaysAgoReviewTime = today.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);

        Review reviewToday = createReview(cardId, ReviewResult.EASY, todayReviewTime);
        Review reviewTwoDaysAgo = createReview(cardId, ReviewResult.HARD, twoDaysAgoReviewTime);

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeckId(deckId)).thenReturn(1);
        when(cardRepository.findByDeckId(deckId)).thenReturn(List.of(card));
        when(reviewRepository.findByCardIdIn(List.of(cardId)))
                .thenReturn(List.of(reviewToday, reviewTwoDaysAgo));

        // When
        StatsResponse stats = statsService.getDeckStats(deckId);

        // Then
        assertThat(stats.studyStreak()).isEqualTo(1);
    }

    @Test
    void getDeckStats_resultDistributionCountsEachType() {
        // Given
        UUID deckId = UUID.randomUUID();
        UUID cardId1 = UUID.randomUUID();
        UUID cardId2 = UUID.randomUUID();

        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Test Deck");

        Card card1 = new Card();
        card1.setId(cardId1);
        card1.setDeckId(deckId);

        Card card2 = new Card();
        card2.setId(cardId2);
        card2.setDeckId(deckId);

        // Create reviews with specific result distribution:
        // 3 EASY, 2 GOOD, 1 HARD, 4 AGAIN
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant baseTime = today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);

        List<Review> reviews = List.of(
                createReview(cardId1, ReviewResult.EASY, baseTime),
                createReview(cardId1, ReviewResult.EASY, baseTime.plusSeconds(60)),
                createReview(cardId1, ReviewResult.EASY, baseTime.plusSeconds(120)),
                createReview(cardId1, ReviewResult.GOOD, baseTime.plusSeconds(180)),
                createReview(cardId2, ReviewResult.GOOD, baseTime.plusSeconds(240)),
                createReview(cardId2, ReviewResult.HARD, baseTime.plusSeconds(300)),
                createReview(cardId2, ReviewResult.AGAIN, baseTime.plusSeconds(360)),
                createReview(cardId2, ReviewResult.AGAIN, baseTime.plusSeconds(420)),
                createReview(cardId1, ReviewResult.AGAIN, baseTime.plusSeconds(480)),
                createReview(cardId1, ReviewResult.AGAIN, baseTime.plusSeconds(540))
        );

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeckId(deckId)).thenReturn(2);
        when(cardRepository.findByDeckId(deckId)).thenReturn(List.of(card1, card2));
        when(reviewRepository.findByCardIdIn(List.of(cardId1, cardId2))).thenReturn(reviews);

        // When
        StatsResponse stats = statsService.getDeckStats(deckId);

        // Then
        assertThat(stats.resultDistribution().easy()).isEqualTo(3);
        assertThat(stats.resultDistribution().good()).isEqualTo(2);
        assertThat(stats.resultDistribution().hard()).isEqualTo(1);
        assertThat(stats.resultDistribution().again()).isEqualTo(4);
    }

    private Review createReview(UUID cardId, ReviewResult result, Instant reviewedAt) {
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setCardId(cardId);
        review.setResult(result);
        review.setReviewedAt(reviewedAt);
        review.setNextReviewAt(reviewedAt.plusSeconds(86400));
        return review;
    }
}
