package com.ice.iceenglish.modules.learning.repository;

import com.ice.iceenglish.modules.learning.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {
    List<Vocabulary> findByVideoIdOrderByCreatedAtAsc(UUID videoId);

    @Modifying
    @Query("delete from Vocabulary v where v.video.id = :videoId")
    int deleteByVideoId(@Param("videoId") UUID videoId);
}
