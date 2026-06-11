package com.flashcards.unit;

import com.flashcards.dto.CreateDeckRequest;
import com.flashcards.dto.DeckResponse;
import com.flashcards.dto.UpdateDeckRequest;
import com.flashcards.entity.Card;
import com.flashcards.entity.Deck;
import com.flashcards.exception.ResourceNotFoundException;
import com.flashcards.repository.CardRepository;
import com.flashcards.repository.DeckRepository;
import com.flashcards.repository.ReviewRepository;
import com.flashcards.service.DeckService;
import jakarta.persistence.EntityManager;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DeckService deckService;

    @Test
    void createDeck_withValidData_returnsDeckResponse() {
        // Given
        CreateDeckRequest request = new CreateDeckRequest("Java Basics", "Core Java concepts");

        Deck savedDeck = new Deck();
        savedDeck.setId(UUID.randomUUID());
        savedDeck.setName("Java Basics");
        savedDeck.setDescription("Core Java concepts");
        savedDeck.setCreatedAt(Instant.now());
        savedDeck.setUpdatedAt(Instant.now());

        when(deckRepository.save(any(Deck.class))).thenReturn(savedDeck);

        // When
        DeckResponse response = deckService.createDeck(request);

        // Then
        assertThat(response.id()).isEqualTo(savedDeck.getId());
        assertThat(response.name()).isEqualTo("Java Basics");
        assertThat(response.description()).isEqualTo("Core Java concepts");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        verify(deckRepository).save(any(Deck.class));
    }

    @Test
    void createDeck_withNullDescription_setsEmptyDescription() {
        // Given
        CreateDeckRequest request = new CreateDeckRequest("Java Basics", null);

        Deck savedDeck = new Deck();
        savedDeck.setId(UUID.randomUUID());
        savedDeck.setName("Java Basics");
        savedDeck.setDescription("");
        savedDeck.setCreatedAt(Instant.now());
        savedDeck.setUpdatedAt(Instant.now());

        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> {
            Deck deck = invocation.getArgument(0);
            assertThat(deck.getDescription()).isEqualTo("");
            return savedDeck;
        });

        // When
        DeckResponse response = deckService.createDeck(request);

        // Then
        assertThat(response.description()).isEqualTo("");
        verify(deckRepository).save(any(Deck.class));
    }

    @Test
    void getDeckById_withNonExistentId_throwsResourceNotFoundException() {
        // Given
        UUID randomId = UUID.randomUUID();
        when(deckRepository.findById(randomId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> deckService.getDeckById(randomId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deck")
                .hasMessageContaining(randomId.toString());
    }

    @Test
    void updateDeck_withNonExistentId_throwsResourceNotFoundException() {
        // Given
        UUID randomId = UUID.randomUUID();
        UpdateDeckRequest request = new UpdateDeckRequest("Updated Name", "Updated Description");
        when(deckRepository.findById(randomId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> deckService.updateDeck(randomId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deck")
                .hasMessageContaining(randomId.toString());
    }

    @Test
    void deleteDeck_cascadesToCardsAndReviews() {
        // Given
        UUID deckId = UUID.randomUUID();
        UUID cardId1 = UUID.randomUUID();
        UUID cardId2 = UUID.randomUUID();

        Deck deck = new Deck();
        deck.setId(deckId);
        deck.setName("Test Deck");
        deck.setDescription("Test");
        deck.setCreatedAt(Instant.now());
        deck.setUpdatedAt(Instant.now());

        Card card1 = new Card();
        card1.setId(cardId1);
        card1.setDeckId(deckId);

        Card card2 = new Card();
        card2.setId(cardId2);
        card2.setDeckId(deckId);

        List<Card> cards = List.of(card1, card2);

        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cardRepository.findByDeckId(deckId)).thenReturn(cards);

        // When
        deckService.deleteDeck(deckId);

        // Then
        verify(reviewRepository).deleteByCardIdIn(List.of(cardId1, cardId2));
        verify(cardRepository).deleteByDeckId(deckId);
        verify(deckRepository).delete(deck);
    }

    @Test
    void listDecks_withEmptyDatabase_returnsEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Deck> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(deckRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(emptyPage);

        // When
        Page<DeckResponse> result = deckService.listDecks(pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }
}
