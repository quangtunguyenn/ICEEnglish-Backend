package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.Submission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    // Lấy danh sách lịch sử làm bài của 1 học sinh (sắp xếp mới nhất lên đầu)
    List<Submission> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Lấy lịch sử làm bài của học sinh đối với 1 đề thi cụ thể
    List<Submission> findByUserIdAndExamIdOrderByCreatedAtDesc(UUID userId, UUID examId);

    Optional<Submission> findFirstByUserUsernameAndExamIdAndSubmittedAtIsNullOrderByStartedAtDesc(
            String username, UUID examId);

    Optional<Submission> findFirstByUserUsernameAndExamIdAndModeOrderByStartedAtDesc(
            String username, UUID examId, String mode);

    Optional<Submission> findFirstByUserIdAndExamIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
            UUID userId, UUID examId, String mode);

    List<Submission> findByUserIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtAsc(
            UUID userId, String mode);

    Optional<Submission> findByIdAndUserUsername(UUID id, String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id and s.user.username = :username")
    Optional<Submission> findByIdAndUserUsernameForUpdate(
            @Param("id") UUID id, @Param("username") String username);
}