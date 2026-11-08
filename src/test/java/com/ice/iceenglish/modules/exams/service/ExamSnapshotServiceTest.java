package com.ice.iceenglish.modules.exams.service;

import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.exams.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExamSnapshotServiceTest {

    @Test
    void loadsPartPassagesAndQuestionOptionsFromEntities() {
        ExamRepository exams = mock(ExamRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        CacheRegistry caches = new CacheRegistry(120, mock(PlatformTransactionManager.class));
        ExamSnapshotService service = new ExamSnapshotService(exams, questions, caches);
        UUID examId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        ExamPart part = ExamPart.builder().id(partId).partName("Cloze").partType(PartType.CLOZE_TEST)
                .passageText("Passage").orderIndex(1).build();
        Exam exam = Exam.builder().id(examId).title("Exam").examType(ExamType.GENERAL_EXAM)
                .status(ExamStatus.PUBLISHED).parts(List.of(part)).build();
        Question question = Question.builder().id(UUID.randomUUID()).part(part).orderIndex(1)
                .content("Question").optA("A text").optB("B text").optC("C text").optD("D text")
                .correctOpt("A").build();
        when(exams.findByIdWithParts(examId)).thenReturn(Optional.of(exam));
        when(questions.findAllWithDetailsByExamId(examId)).thenReturn(List.of(question));

        ExamSnapshot snapshot = service.find(examId).orElseThrow();

        assertEquals("Passage", snapshot.parts().get(0).passageText());
        ExamSnapshot.QuestionSnap result = snapshot.questions().get(0);
        assertEquals(partId, result.partId());
        assertEquals("A text", result.optA());
        assertEquals("B text", result.optB());
        assertEquals("C text", result.optC());
        assertEquals("D text", result.optD());
    }
}
