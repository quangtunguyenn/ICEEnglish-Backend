package com.ice.iceenglish.modules.exams.repository;

import com.ice.iceenglish.modules.exams.entity.ExamPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamPartRepository extends JpaRepository<ExamPart, UUID> {

    List<ExamPart> findByExamIdOrderByOrderIndexAsc(UUID examId);

    /**
     * Nạp questions vào các ExamPart đang được quản lý trong persistence context
     * (cùng instance với các part đã load từ ExamRepository#findByIdWithParts).
     * Thứ tự questions được đảm bảo bởi @OrderBy trên ExamPart#questions.
     */
    @Query("SELECT DISTINCT p FROM ExamPart p LEFT JOIN FETCH p.questions WHERE p.exam.id = :examId")
    List<ExamPart> fetchQuestionsByExamId(@Param("examId") UUID examId);
}
