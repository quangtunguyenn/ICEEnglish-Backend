package com.ice.iceenglish.modules.learning.repository;

import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {
    List<Vocabulary> findByVideoIdOrderByCreatedAtAsc(UUID videoId);
}
