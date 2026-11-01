package com.ice.iceenglish.modules.exams.repository;

import com.ice.iceenglish.modules.exams.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByPartIdOrderByOrderIndexAsc(UUID partId);

    List<Question> findByPartExamIdOrderByOrderIndexAsc(UUID examId);
}
