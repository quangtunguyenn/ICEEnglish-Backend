package com.ice.iceenglish.modules.learning.repository;

import com.ice.iceenglish.modules.learning.entity.FavoriteVocabulary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteVocabularyRepository extends JpaRepository<FavoriteVocabulary, UUID> {
    List<FavoriteVocabulary> findByUserUsernameOrderByCreatedAtDesc(String username);

    Optional<FavoriteVocabulary> findByUserUsernameAndWordIgnoreCaseAndMeaningIgnoreCase(
            String username, String word, String meaning);

    Optional<FavoriteVocabulary> findByIdAndUserUsername(UUID id, String username);
}
