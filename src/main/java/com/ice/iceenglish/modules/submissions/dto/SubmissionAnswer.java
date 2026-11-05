package com.ice.iceenglish.modules.submissions.dto;

import java.util.UUID;

/** Đáp án một câu của một lượt thi (không kèm entity câu hỏi). */
public record SubmissionAnswer(UUID questionId, String selectedOpt, Boolean isCorrect) {}
