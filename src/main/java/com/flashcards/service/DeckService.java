package com.flashcards.service;

import com.flashcards.dto.CreateDeckRequest;
import com.flashcards.dto.DeckResponse;
import com.flashcards.dto.UpdateDeckRequest;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;

    public DeckService(DeckRepository deckRepository,
                       CardRepository cardRepository,
                       ReviewRepository reviewRepository,
                       EntityManager entityManager) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.reviewRepository = reviewRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public DeckResponse createDeck(CreateDeckRequest request) {
        Deck deck = new Deck();
        deck.setName(request.name());
        deck.setDescription(request.description() == null ? "" : request.description());

        Deck saved = deckRepository.save(deck);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DeckResponse> listDecks(Pageable pageable) {
        return deckRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DeckResponse getDeckById(UUID id) {
        Deck deck = deckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", "id", id));
        return toResponse(deck);
    }

    @Transactional
    public DeckResponse updateDeck(UUID id, UpdateDeckRequest request) {
        Deck deck = deckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", "id", id));

        deck.setName(request.name());
        deck.setDescription(request.description() == null ? "" : request.description());

        Deck updated = deckRepository.save(deck);
        return toResponse(updated);
    }

    @Transactional
    public void deleteDeck(UUID id) {
        Deck deck = deckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", "id", id));

        // Cascade delete: find all cards, collect IDs, delete reviews, then cards, then deck
        List<Card> cards = cardRepository.findByDeckId(deck.getId());
        if (!cards.isEmpty()) {
            List<UUID> cardIds = cards.stream().map(Card::getId).toList();
            reviewRepository.deleteByCardIdIn(cardIds);
            entityManager.flush();
            cardRepository.deleteByDeckId(deck.getId());
        }

        deckRepository.delete(deck);
    }

    private DeckResponse toResponse(Deck deck) {
        return new DeckResponse(
                deck.getId(),
                deck.getName(),
                deck.getDescription(),
                deck.getCreatedAt(),
                deck.getUpdatedAt()
        );
    }
}
