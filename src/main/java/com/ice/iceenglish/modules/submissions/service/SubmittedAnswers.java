package com.ice.iceenglish.modules.submissions.service;

import com.ice.iceenglish.modules.exams.dto.ExamSnapshot;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Kiểm tra và chuẩn hoá danh sách đáp án học sinh gửi lên. */
final class SubmittedAnswers {

    private static final Set<String> OPTIONS = Set.of("A", "B", "C", "D");

    private SubmittedAnswers() {
    }

    /** Trả về questionId -> đáp án (A-D, hoặc null nếu bỏ trống). */
    static Map<UUID, String> parse(SubmitExamRequest request,
            Map<UUID, ExamSnapshot.QuestionSnap> questionMap) {
        Map<UUID, String> answers = new HashMap<>();
        for (SubmitExamRequest.UserAnswerDto answer : Optional.ofNullable(request.getAnswers())
                .orElseGet(Collections::emptyList)) {
            if (answer == null || answer.getQuestionId() == null) {
                throw new IllegalArgumentException("Mỗi đáp án phải có questionId.");
            }
            if (!questionMap.containsKey(answer.getQuestionId())) {
                throw new IllegalArgumentException(
                        "Câu hỏi " + answer.getQuestionId() + " không thuộc đề thi.");
            }
            if (answers.containsKey(answer.getQuestionId())) {
                throw new IllegalArgumentException("Một câu hỏi chỉ được gửi một đáp án.");
            }
            answers.put(answer.getQuestionId(), normalize(answer.getSelectedOpt()));
        }
        return answers;
    }

    private static String normalize(String selectedOpt) {
        if (selectedOpt == null || selectedOpt.isBlank()) {
            return null;
        }
        String option = selectedOpt.trim().toUpperCase(Locale.ROOT);
        if (!OPTIONS.contains(option)) {
            throw new IllegalArgumentException("Đáp án chỉ được là A, B, C hoặc D.");
        }
        return option;
    }
}
