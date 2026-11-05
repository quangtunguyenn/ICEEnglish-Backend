package com.ice.iceenglish.modules.exams.repository;

import com.ice.iceenglish.modules.exams.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByPartIdOrderByOrderIndexAsc(UUID partId);

    /** Lấy mọi câu hỏi của đề: qua part (đề thường) hoặc trực tiếp (HOMEWORK). */
    @Query("SELECT q FROM Question q LEFT JOIN q.part p "
            + "WHERE p.exam.id = :examId OR q.exam.id = :examId ORDER BY q.orderIndex ASC")
    List<Question> findAllByExamId(@Param("examId") UUID examId);

    /** Câu hỏi của đề thường, nạp sẵn part và video liên quan. */
    @Query("SELECT q FROM Question q JOIN FETCH q.part p LEFT JOIN FETCH q.relatedVideo "
            + "WHERE p.exam.id = :examId")
    List<Question> findByPartExamIdWithDetails(@Param("examId") UUID examId);

    /** Câu hỏi nối thẳng vào exam (HOMEWORK), nạp sẵn video liên quan. */
    @Query("SELECT q FROM Question q LEFT JOIN FETCH q.relatedVideo WHERE q.exam.id = :examId")
    List<Question> findByExamIdWithDetails(@Param("examId") UUID examId);

    /** Như findAllByExamId nhưng tách thành 2 truy vấn đơn giản (dùng được index) và nạp sẵn quan hệ. */
    default List<Question> findAllWithDetailsByExamId(UUID examId) {
        List<Question> result = new ArrayList<>(findByPartExamIdWithDetails(examId));
        result.addAll(findByExamIdWithDetails(examId));
        result.sort(Comparator.comparing(Question::getOrderIndex));
        return result;
    }
}
