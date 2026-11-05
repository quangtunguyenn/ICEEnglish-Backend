package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.PartTimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PartTimeLogRepository extends JpaRepository<PartTimeLog, UUID> {

    Optional<PartTimeLog> findBySubmissionIdAndPartId(UUID submissionId, UUID partId);

    List<PartTimeLog> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    @Query("SELECT l FROM PartTimeLog l JOIN FETCH l.part p "
            + "WHERE l.submission.id = :submissionId ORDER BY l.createdAt ASC, p.orderIndex ASC")
    List<PartTimeLog> findBySubmissionIdWithPart(@Param("submissionId") UUID submissionId);
}
