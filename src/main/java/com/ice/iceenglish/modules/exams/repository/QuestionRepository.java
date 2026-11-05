package com.ice.iceenglish.modules.exams.repository;

import com.ice.iceenglish.modules.exams.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByPartIdOrderByOrderIndexAsc(UUID partId);

    /** Lấy mọi câu hỏi của đề: qua part (đề thường) hoặc trực tiếp (HOMEWORK). */
    @Query("SELECT q FROM Question q LEFT JOIN q.part p "
            + "WHERE p.exam.id = :examId OR q.exam.id = :examId ORDER BY q.orderIndex ASC")
    List<Question> findAllByExamId(@Param("examId") UUID examId);
}
