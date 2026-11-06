package com.ice.iceenglish.modules.exams.dto;

import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bản chụp bất biến của một đề thi (đề, part, câu hỏi, đáp án, video liên quan) dùng cho bắt đầu thi,
 * chấm điểm và dựng kết quả mà không phải nạp lại hàng trăm entity từ DB ở mỗi lượt.
 */
public final class ExamSnapshot {

    public record PartSnap(UUID id, String name, PartType partType, String passageText,
                           String translate, Integer orderIndex) {}

    public record VideoSnap(UUID id, String title, String url) {}

    public record QuestionSnap(UUID id, Integer orderIndex, String content,
                               String optA, String optB, String optC, String optD, String correctOpt,
                               String explanation, UUID partId, PartType partType, VideoSnap video) {}

    private final UUID examId;
    private final String title;
    private final ExamType examType;
    private final ExamStatus status;
    private final Integer durationMinutes;
    private final List<PartSnap> parts;
    private final List<QuestionSnap> questions;
    private final Set<UUID> partIds;
    private final Map<UUID, QuestionSnap> questionsById;

    public ExamSnapshot(UUID examId, String title, ExamType examType, ExamStatus status,
                        Integer durationMinutes, List<PartSnap> parts, List<QuestionSnap> questions) {
        this.examId = examId;
        this.title = title;
        this.examType = examType;
        this.status = status;
        this.durationMinutes = durationMinutes;
        this.parts = List.copyOf(parts);
        this.questions = List.copyOf(questions);
        this.partIds = this.parts.stream().map(PartSnap::id).collect(Collectors.toUnmodifiableSet());
        Map<UUID, QuestionSnap> byId = new HashMap<>();
        this.questions.forEach(q -> byId.put(q.id(), q));
        this.questionsById = Map.copyOf(byId);
    }

    public UUID examId() { return examId; }
    public String title() { return title; }
    public ExamType examType() { return examType; }
    public ExamStatus status() { return status; }
    public Integer durationMinutes() { return durationMinutes; }
    /** Các part theo thứ tự order_index. */
    public List<PartSnap> parts() { return parts; }
    /** Mọi câu hỏi của đề theo thứ tự order_index. */
    public List<QuestionSnap> questions() { return questions; }
    public Set<UUID> partIds() { return partIds; }
    public Map<UUID, QuestionSnap> questionsById() { return questionsById; }
    public int totalQuestions() { return questions.size(); }
}
