package com.flashcards.service;

import com.flashcards.dto.CardResponse;
import com.flashcards.dto.CreateCardRequest;
import com.flashcards.dto.UpdateCardRequest;
import com.flashcards.entity.Card;
import com.flashcards.entity.Review;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CardService {

    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final ReviewRepository reviewRepository;

    public CardService(CardRepository cardRepository, DeckRepository deckRepository, ReviewRepository reviewRepository) {
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.reviewRepository = reviewRepository;
    }

    public CardResponse createCard(UUID deckId, CreateCardRequest request) {
        if (!deckRepository.existsById(deckId)) {
            throw new ResourceNotFoundException("Deck", "id", deckId);
        }

        Card card = new Card();
        card.setDeckId(deckId);
        card.setFront(request.front());
        card.setBack(request.back());

        Card saved = cardRepository.save(card);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> listCards(UUID deckId, String q, Pageable pageable) {
        if (!deckRepository.existsById(deckId)) {
            throw new ResourceNotFoundException("Deck", "id", deckId);
        }

        Page<Card> cards;
        if (q == null || q.isBlank()) {
            cards = cardRepository.findByDeckIdOrderByCreatedAtDesc(deckId, pageable);
        } else {
            cards = cardRepository.searchByDeckId(deckId, q, pageable);
        }

        return cards.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CardResponse getCardById(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", id));
        return toResponse(card);
    }

    public CardResponse updateCard(UUID id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", id));

        card.setFront(request.front());
        card.setBack(request.back());

        Card saved = cardRepository.save(card);
        return toResponse(saved);
    }

    public void deleteCard(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card", "id", id));

        reviewRepository.deleteByCardId(card.getId());
        cardRepository.delete(card);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCardsForStudy(UUID deckId) {
        if (!deckRepository.existsById(deckId)) {
            throw new ResourceNotFoundException("Deck", "id", deckId);
        }

        Instant now = Instant.now();
        List<Card> cards = cardRepository.findCardsForStudy(deckId, now, PageRequest.of(0, 20));

        if (cards.isEmpty()) {
            return List.of();
        }

        // Get the most recent review for each card to determine ordering
        List<UUID> cardIds = cards.stream().map(Card::getId).collect(Collectors.toList());
        List<Review> reviews = reviewRepository.findByCardIdIn(cardIds);

        // Build a map of cardId -> most recent review's nextReviewAt
        Map<UUID, Instant> latestNextReviewAtByCardId = reviews.stream()
                .collect(Collectors.groupingBy(
                        Review::getCardId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(Review::getReviewedAt)),
                                opt -> opt.map(Review::getNextReviewAt).orElse(null)
                        )
                ));

        // Sort: never-reviewed cards first (by createdAt asc), then due cards (by nextReviewAt asc)
        cards.sort((a, b) -> {
            Instant nextA = latestNextReviewAtByCardId.get(a.getId());
            Instant nextB = latestNextReviewAtByCardId.get(b.getId());
            boolean neverReviewedA = (nextA == null);
            boolean neverReviewedB = (nextB == null);

            if (neverReviewedA && neverReviewedB) {
                // Both never reviewed: order by createdAt ascending
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            } else if (neverReviewedA) {
                // A is never reviewed, comes first
                return -1;
            } else if (neverReviewedB) {
                // B is never reviewed, comes first
                return 1;
            } else {
                // Both have reviews: order by nextReviewAt ascending
                return nextA.compareTo(nextB);
            }
        });

        return cards.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private CardResponse toResponse(Card card) {
        return new CardResponse(
                card.getId(),
                card.getDeckId(),
                card.getFront(),
                card.getBack(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
