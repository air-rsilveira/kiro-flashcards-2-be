package com.flashcards.dto;

public record ResultDistribution(
        long easy,
        long good,
        long hard,
        long again) {
}
