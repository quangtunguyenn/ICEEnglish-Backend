package com.ice.iceenglish.modules.submissions.repository;

import com.ice.iceenglish.modules.submissions.dto.SubmissionAnswer;
import com.ice.iceenglish.modules.submissions.entity.SubDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubDetailRepository extends JpaRepository<SubDetail, UUID> {

    // Lấy danh sách chi tiết đáp án đã chọn theo lượt thi (Submission)
    List<SubDetail> findBySubmissionId(UUID submissionId);

    /** Chỉ đọc đáp án đã chọn (không nạp entity câu hỏi); nội dung câu hỏi lấy từ ExamSnapshot. */
    @Query("SELECT new com.ice.iceenglish.modules.submissions.dto.SubmissionAnswer("
            + "d.question.id, d.selectedOpt, d.isCorrect) FROM SubDetail d WHERE d.submission.id = :submissionId")
    List<SubmissionAnswer> findAnswersBySubmissionId(@Param("submissionId") UUID submissionId);
}