package com.flashcards.controller;

import com.flashcards.dto.CreateReviewRequest;
import com.flashcards.dto.ReviewResponse;
import com.flashcards.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ReviewResponse> registerReview(
            @PathVariable UUID id,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.registerReview(id, request.result());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
