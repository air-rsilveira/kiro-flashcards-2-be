package com.flashcards.unit;

import com.flashcards.dto.CardResponse;
import com.flashcards.dto.CreateCardRequest;
import com.flashcards.entity.Card;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import com.flashcards.service.CardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    @DisplayName("createCard with non-existent deckId throws ResourceNotFoundException")
    void createCard_nonExistentDeck_throwsResourceNotFoundException() {
        // Validates: Requirement 6.2
        UUID deckId = UUID.randomUUID();
        CreateCardRequest request = new CreateCardRequest("front text", "back text");

        when(deckRepository.existsById(deckId)).thenReturn(false);

        assertThatThrownBy(() -> cardService.createCard(deckId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deck")
                .hasMessageContaining(deckId.toString());

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("listCards with search term calls searchByDeckId (case-insensitive matching)")
    void listCards_withSearchTerm_callsSearchByDeckId() {
        // Validates: Requirement 7.3
        UUID deckId = UUID.randomUUID();
        String searchTerm = "hello";
        Pageable pageable = PageRequest.of(0, 10);

        when(deckRepository.existsById(deckId)).thenReturn(true);
        when(cardRepository.searchByDeckId(eq(deckId), eq(searchTerm), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        cardService.listCards(deckId, searchTerm, pageable);

        verify(cardRepository).searchByDeckId(deckId, searchTerm, pageable);
        verify(cardRepository, never()).findByDeckIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("listCards with blank search term calls findByDeckIdOrderByCreatedAtDesc")
    void listCards_blankSearchTerm_callsFindByDeckId() {
        // Validates: Requirement 7.6
        UUID deckId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        when(deckRepository.existsById(deckId)).thenReturn(true);
        when(cardRepository.findByDeckIdOrderByCreatedAtDesc(eq(deckId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        cardService.listCards(deckId, "   ", pageable);

        verify(cardRepository).findByDeckIdOrderByCreatedAtDesc(deckId, pageable);
        verify(cardRepository, never()).searchByDeckId(any(), any(), any());
    }

    @Test
    @DisplayName("listCards with null search term calls findByDeckIdOrderByCreatedAtDesc")
    void listCards_nullSearchTerm_callsFindByDeckId() {
        // Validates: Requirement 7.6
        UUID deckId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        when(deckRepository.existsById(deckId)).thenReturn(true);
        when(cardRepository.findByDeckIdOrderByCreatedAtDesc(eq(deckId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        cardService.listCards(deckId, null, pageable);

        verify(cardRepository).findByDeckIdOrderByCreatedAtDesc(deckId, pageable);
        verify(cardRepository, never()).searchByDeckId(any(), any(), any());
    }

    @Test
    @DisplayName("deleteCard removes associated reviews before deleting the card")
    void deleteCard_removesReviewsBeforeDeletingCard() {
        // Validates: Requirement 10.4
        UUID cardId = UUID.randomUUID();
        Card card = new Card();
        card.setId(cardId);
        card.setDeckId(UUID.randomUUID());
        card.setFront("front");
        card.setBack("back");
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        cardService.deleteCard(cardId);

        var inOrder = inOrder(reviewRepository, cardRepository);
        inOrder.verify(reviewRepository).deleteByCardId(cardId);
        inOrder.verify(cardRepository).delete(card);
    }
}
