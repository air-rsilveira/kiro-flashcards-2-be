package com.flashcards.dto;

public record StatsResponse(
        int totalCards,
        int cardsStudied,
        int cardsDue,
        long totalReviews,
        int studyStreak,
        ResultDistribution resultDistribution) {
}
