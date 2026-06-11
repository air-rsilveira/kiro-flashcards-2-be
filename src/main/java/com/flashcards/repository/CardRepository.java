package com.flashcards.repository;

import com.flashcards.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    Page<Card> findByDeckIdOrderByCreatedAtDesc(UUID deckId, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.deckId = :deckId AND (LOWER(c.front) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(c.back) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<Card> searchByDeckId(@Param("deckId") UUID deckId, @Param("term") String term, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.deckId = :deckId AND (NOT EXISTS (SELECT r FROM Review r WHERE r.cardId = c.id) OR EXISTS (SELECT r FROM Review r WHERE r.cardId = c.id AND r.reviewedAt = (SELECT MAX(r2.reviewedAt) FROM Review r2 WHERE r2.cardId = c.id) AND r.nextReviewAt <= :now))")
    List<Card> findCardsForStudy(@Param("deckId") UUID deckId, @Param("now") Instant now, Pageable pageable);

    int countByDeckId(UUID deckId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Card c WHERE c.deckId = :deckId")
    void deleteByDeckId(@Param("deckId") UUID deckId);

    List<Card> findByDeckId(UUID deckId);
}
