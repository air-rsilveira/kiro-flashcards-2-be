package com.flashcards.repository;

import com.flashcards.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findTopByCardIdOrderByReviewedAtDesc(UUID cardId);

    long countByCardIdIn(List<UUID> cardIds);

    List<Review> findByCardIdIn(List<UUID> cardIds);

    @Modifying
    @Transactional
    void deleteByCardId(UUID cardId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    void deleteByCardIdIn(List<UUID> cardIds);
}
