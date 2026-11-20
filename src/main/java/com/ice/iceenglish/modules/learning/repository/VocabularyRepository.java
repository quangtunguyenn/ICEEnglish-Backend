package com.ice.iceenglish.modules.learning.repository;

import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {
    List<Vocabulary> findByVideoIdOrderByCreatedAtAsc(UUID videoId);

    Optional<Vocabulary> findFirstByWordIgnoreCaseAndMeaningIgnoreCaseOrderByCreatedAtAsc(
            String word, String meaning);

    @Query(value = "select * from vocabularies order by random()", nativeQuery = true)
    List<Vocabulary> findRandomVocabularyOptions(Pageable pageable);

    @Modifying
    @Query("delete from Vocabulary v where v.video.id = :videoId")
    int deleteByVideoId(@Param("videoId") UUID videoId);
}
