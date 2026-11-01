package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Submission> findByIdAndUserUsername(UUID id, String username);
}