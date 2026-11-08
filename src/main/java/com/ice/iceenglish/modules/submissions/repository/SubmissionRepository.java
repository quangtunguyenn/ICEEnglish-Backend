package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.Submission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

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

    Page<Submission> findByUserUsernameAndExamIdAndModeAndSubmittedAtIsNotNull(
            String username, UUID examId, String mode, Pageable pageable);

    Optional<Submission> findByIdAndUserUsernameAndModeAndSubmittedAtIsNotNull(
            UUID id, String username, String mode);

    @Query("select distinct s.exam.id from Submission s "
            + "where s.user.username = :username and s.exam.id in :examIds "
            + "and s.mode = 'GENERAL_EXAM' and s.submittedAt is not null")
    List<UUID> findSubmittedPracticeExamIds(
            @Param("username") String username, @Param("examIds") List<UUID> examIds);

    @Query("select distinct s.exam.id from Submission s "
            + "where s.user.username = :username and s.exam.id in :examIds and s.mode = :mode")
    List<UUID> findAttemptedExamIds(
            @Param("username") String username,
            @Param("mode") String mode,
            @Param("examIds") List<UUID> examIds);

    Optional<Submission> findFirstByUserIdAndExamIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
            UUID userId, UUID examId);

    List<Submission> findByUserIdAndModeAndSubmittedAtIsNotNullOrderBySubmittedAtAsc(
            UUID userId, String mode);

    Page<Submission> findByUserIdAndModeAndSubmittedAtIsNotNull(
            UUID userId, String mode, Pageable pageable);

    @Query("""
            select s.exam.id as examId, s.totalCorrect as totalCorrect, s.totalQuestions as totalQuestions
            from Submission s
            where s.user.username = :username
              and s.mode = :mode
              and s.submittedAt is not null
              and s.exam.id in :examIds
              and s.submittedAt = (
                  select max(latest.submittedAt)
                  from Submission latest
                  where latest.user.username = :username
                    and latest.mode = :mode
                    and latest.exam.id = s.exam.id
                    and latest.submittedAt is not null
              )
            order by s.submittedAt desc, s.id desc
            """)
    List<LatestExamScore> findLatestScoresByExamIds(
            @Param("username") String username,
            @Param("mode") String mode,
            @Param("examIds") List<UUID> examIds);

    interface LatestExamScore {
        UUID getExamId();
        Integer getTotalCorrect();
        Integer getTotalQuestions();
    }

    Optional<Submission> findByIdAndUserUsername(UUID id, String username);

    @Query("select s.id as submissionId, s.exam.id as examId, s.user.id as userId, "
            + "s.user.fullName as fullName, s.totalCorrect as totalCorrect, s.totalQuestions as totalQuestions, "
            + "s.startedAt as startedAt, s.submittedAt as submittedAt "
            + "from Submission s where s.exam.id = :examId and s.mode = 'MOCK' "
            + "and s.submittedAt is not null")
    List<MockTopData> findMockTopDataByExamId(@Param("examId") UUID examId);

    @Query("select s.id as submissionId, s.exam.id as examId, s.user.id as userId, "
            + "s.user.fullName as fullName, s.totalCorrect as totalCorrect, s.totalQuestions as totalQuestions, "
            + "s.startedAt as startedAt, s.submittedAt as submittedAt "
            + "from Submission s where s.exam.id in :examIds and s.mode = 'MOCK' "
            + "and s.submittedAt is not null")
    List<MockTopData> findMockTopDataByExamIds(@Param("examIds") List<UUID> examIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id and s.user.username = :username")
    Optional<Submission> findByIdAndUserUsernameForUpdate(
            @Param("id") UUID id, @Param("username") String username);

    interface MockTopData {
        Integer getTotalCorrect();
        Integer getTotalQuestions();
        LocalDateTime getStartedAt();
        LocalDateTime getSubmittedAt();
        UUID getSubmissionId();
        UUID getExamId();
        UUID getUserId();
        String getFullName();
    }
}