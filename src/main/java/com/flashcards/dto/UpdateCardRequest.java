package com.flashcards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCardRequest(
        @NotBlank @Size(max = 5000) String front,
        @NotBlank @Size(max = 5000) String back) {
}
