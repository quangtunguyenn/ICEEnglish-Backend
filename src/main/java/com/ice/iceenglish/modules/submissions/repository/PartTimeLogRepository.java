package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.PartTimeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface PartTimeLogRepository extends JpaRepository<PartTimeLog, UUID> {

    Optional<PartTimeLog> findBySubmissionIdAndPartId(UUID submissionId, UUID partId);

    List<PartTimeLog> findBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);
}
