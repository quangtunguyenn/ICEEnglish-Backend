package com.ice.iceenglish.modules.exams.repository;

import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /**
     * Chỉ fetch Exam + Parts. Không fetch thêm Questions trong cùng query vì Hibernate
     * không cho fetch 2 collection kiểu List (MultipleBagFetchException).
     * Questions được nạp bằng ExamPartRepository#fetchQuestionsByExamId trong cùng transaction.
     * Thứ tự parts được đảm bảo bởi @OrderBy trên Exam#parts.
     */
    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN FETCH e.parts WHERE e.id = :id")
    Optional<Exam> findByIdWithParts(@Param("id") UUID id);

    @Query("SELECT DISTINCT e FROM Exam e LEFT JOIN FETCH e.parts " +
            "WHERE e.video.id = :videoId AND e.examType = :examType " +
            "ORDER BY e.createdAt DESC")
    List<Exam> findByVideoIdAndExamTypeWithParts(
            @Param("videoId") UUID videoId, @Param("examType") ExamType examType);

    List<Exam> findByStatusOrderByCreatedAtDesc(ExamStatus status);

    List<Exam> findByStatusAndExamTypeOrderByCreatedAtDesc(
            ExamStatus status, com.ice.iceenglish.modules.exams.enums.ExamType examType);

    Page<Exam> findByExamTypeAndStatusAndTitleContainingIgnoreCase(
            ExamType examType, ExamStatus status, String title, Pageable pageable);

    List<Exam> findByExamTypeAndStatus(ExamType examType, ExamStatus status, Sort sort);

    List<Exam> findByExamType(ExamType examType, Sort sort);

    List<Exam> findByStatus(ExamStatus status, Sort sort);

    @Query("""
            select e.id as examId,
                   (select count(q) from Question q where q.exam = e)
                     + (select count(qp) from Question qp where qp.part.exam = e) as totalQuestions
            from Exam e
            where e.id in :examIds
            """)
    List<ExamQuestionCount> findQuestionCountsByExamIds(@Param("examIds") List<UUID> examIds);

    interface ExamQuestionCount {
        UUID getExamId();
        Long getTotalQuestions();
    }
}
