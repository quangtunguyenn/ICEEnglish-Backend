package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.modules.exams.dto.ExamImportDto;
import com.ice.iceenglish.modules.exams.dto.ExamImportResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamPreviewResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.entity.Exam;
import com.ice.iceenglish.modules.exams.entity.ExamPart;
import com.ice.iceenglish.modules.exams.entity.Question;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.enums.PartType;
import com.ice.iceenglish.modules.exams.exception.ConflictException;
import com.ice.iceenglish.modules.exams.exception.ExamImportException;
import com.ice.iceenglish.modules.exams.exception.NotFoundException;
import com.ice.iceenglish.modules.exams.repository.ExamPartRepository;
import com.ice.iceenglish.modules.exams.repository.ExamRepository;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamPartRepository examPartRepository;
    private final VideoRepository videoRepository;
    private final ExamImportValidator importValidator;
    private final ObjectMapper objectMapper;

    // =====================================================================
    // IMPORT
    // =====================================================================

    /**
     * Import đề từ file JSON giáo viên upload. Đề luôn được tạo ở trạng thái DRAFT;
     * giáo viên xem preview rồi mới publish.
     */
    @Transactional
    public ExamImportResponseDto importExam(MultipartFile file, ExamType examType,
                                            boolean answersRevealed, String createdBy) {
        ExamImportDto dto = parse(file);

        ExamImportValidator.Report report = importValidator.validate(dto);
        List<String> errors = new ArrayList<>(report.errors());
        ExamType effectiveExamType = examType;
        if (effectiveExamType == null && dto.getExam() != null
                && dto.getExam().getExamType() != null) {
            try {
                effectiveExamType = ExamType.valueOf(dto.getExam().getExamType().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("exam.exam_type: giá trị không hợp lệ.");
            }
        }
        if (effectiveExamType == null) {
            errors.add("examType: bắt buộc (hoặc khai báo exam.exam_type trong JSON).");
        }
        String jsonExamType = dto.getExam() != null ? dto.getExam().getExamType() : null;
        if (effectiveExamType != null && jsonExamType != null
                && !effectiveExamType.name().equalsIgnoreCase(jsonExamType.trim())) {
            errors.add("exam.exam_type: không khớp với examType trong form upload.");
        }
        Integer importedDuration = durationMinutes(dto);
        if (effectiveExamType == ExamType.MOCK && importedDuration == null) {
            errors.add("duration_minutes: bắt buộc khi examType là MOCK.");
        }
        if (effectiveExamType != ExamType.MOCK && importedDuration != null) {
            errors.add("duration_minutes: chỉ được khai báo khi examType là MOCK.");
        }

        if (dto.getParts() != null) {
            for (int i = 0; i < dto.getParts().size(); i++) {
                ExamImportDto.PartImport part = dto.getParts().get(i);
                if (part == null || part.getQuestions() == null) {
                    continue;
                }
                List<ExamImportDto.QuestionImport> questions = part.getQuestions();
                for (int j = 0; j < questions.size(); j++) {
                    ExamImportDto.QuestionImport question = questions.get(j);
                    if (question == null) {
                        continue;
                    }
                    UUID relatedVideoId = question.getRelatedVideoId();
                    if (relatedVideoId != null && videoRepository.findById(relatedVideoId).isEmpty()) {
                        errors.add("parts[" + i + "].questions[" + j
                                + "].related_video_id: không tìm thấy video.");
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ExamImportException(errors);
        }

        Exam exam = Exam.builder()
                .title(examTitle(dto).trim())
                .examType(effectiveExamType)
                .status(ExamStatus.DRAFT)
                .durationMinutes(effectiveExamType == ExamType.MOCK
                        ? importedDuration : null)
                .answersRevealed((answersRevealed || answersRevealedInJson(dto))
                        && effectiveExamType == ExamType.HOMEWORK)
                .createdBy(createdBy)
                .build();

        int totalQuestions = 0;
        for (ExamImportDto.PartImport p : dto.getParts()) {
            ExamPart part = ExamPart.builder()
                    .partType(PartType.parse(p.getPartType()))
                    .partName(p.getPartName().trim())
                    .passageText(blankToNull(p.getPassageText()))
                    .recommendedTimeSeconds(p.getRecommendedTimeSeconds())
                    .orderIndex(p.getOrderIndex())
                    .build();

            for (ExamImportDto.QuestionImport q : p.getQuestions()) {
                Map<String, String> options = optionsOf(q);
                Video questionVideo = q.getRelatedVideoId() == null
                        ? null : videoRepository.findById(q.getRelatedVideoId()).orElse(null);
                part.addQuestion(Question.builder()
                        .orderIndex(q.getOrderIndex())
                        .content(ExamImportValidator.stripQuestionPrefix(q.getContent()))
                        .optA(options.get("A").trim())
                        .optB(options.get("B").trim())
                        .optC(options.get("C").trim())
                        .optD(options.get("D").trim())
                        .correctOpt(q.getCorrectOption().trim())
                        .explanation(blankToNull(q.getExplanation()))
                        .relatedVideo(questionVideo)
                        .build());
                totalQuestions++;
            }
            exam.addPart(part);
        }

        Exam saved = examRepository.save(exam);

        return ExamImportResponseDto.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .status(saved.getStatus())
                .totalParts(saved.getParts().size())
                .totalQuestions(totalQuestions)
                .warnings(report.warnings())
                .build();
    }

    private static String examTitle(ExamImportDto dto) {
        return dto.getExamTitle() != null ? dto.getExamTitle() : dto.getExam().getTitle();
    }

    private static Integer durationMinutes(ExamImportDto dto) {
        return dto.getDurationMinutes() != null
                ? dto.getDurationMinutes()
                : dto.getExam() != null ? dto.getExam().getDurationMinutes() : null;
    }

    private static boolean answersRevealedInJson(ExamImportDto dto) {
        return dto.getExam() != null && Boolean.TRUE.equals(dto.getExam().getAnswersRevealed());
    }

    private static Map<String, String> optionsOf(ExamImportDto.QuestionImport question) {
        if (question.getOptions() != null) {
            return question.getOptions();
        }
        return Map.of(
                "A", question.getOptA(),
                "B", question.getOptB(),
                "C", question.getOptC(),
                "D", question.getOptD());
    }

    private ExamImportDto parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExamImportException("File upload rỗng hoặc không được gửi.");
        }
        try (InputStream in = file.getInputStream()) {
            return objectMapper.readValue(in, ExamImportDto.class);
        } catch (JsonProcessingException e) {
            JsonLocation loc = e.getLocation();
            String where = loc != null ? " (dòng " + loc.getLineNr() + ", cột " + loc.getColumnNr() + ")" : "";
            throw new ExamImportException("JSON không hợp lệ" + where + ": " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new ExamImportException("Không đọc được file upload: " + e.getMessage());
        }
    }

    // =====================================================================
    // ĐỌC ĐỀ
    // =====================================================================

    /**
     * Đề cho học sinh làm bài (không có đáp án). Học sinh chỉ thấy đề PUBLISHED;
     * đề chưa publish trả 404 để không lộ sự tồn tại của nó.
     */
    @Transactional(readOnly = true)
    public ExamResponseDto getExamDetail(UUID examId, boolean staff) {
        Exam exam = findWithParts(examId);
        if (!staff && exam.getStatus() != ExamStatus.PUBLISHED) {
            throw examNotFound(examId);
        }

        examPartRepository.fetchQuestionsByExamId(examId);
        return toStudentDto(exam);
    }

    @Transactional(readOnly = true)
    public List<ExamResponseDto> listPublishedExams() {
        return examRepository.findByStatusOrderByCreatedAtDesc(ExamStatus.PUBLISHED).stream()
                .map(exam -> ExamResponseDto.builder()
                        .id(exam.getId())
                        .title(exam.getTitle())
                        .examType(exam.getExamType())
                        .durationMinutes(exam.getDurationMinutes())
                        .answersRevealed(exam.isAnswersRevealed())
                        .parts(List.of())
                        .build())
                .toList();
    }

    /** Đề đầy đủ đáp án + lời giải cho giáo viên/admin duyệt, ở mọi trạng thái. */
    @Transactional(readOnly = true)
    public ExamPreviewResponseDto getExamPreview(UUID examId) {
        Exam exam = findWithParts(examId);
        examPartRepository.fetchQuestionsByExamId(examId);
        return toPreviewDto(exam);
    }

    // =====================================================================
    // TRẠNG THÁI
    // =====================================================================

    @Transactional
    public void publish(UUID examId) {
        Exam exam = findExam(examId);
        if (exam.getStatus() == ExamStatus.ARCHIVED) {
            throw new ConflictException("Đề thi đã được lưu trữ nên không thể xuất bản.");
        }
        exam.setStatus(ExamStatus.PUBLISHED);
    }

    @Transactional
    public void archive(UUID examId) {
        findExam(examId).setStatus(ExamStatus.ARCHIVED);
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private Exam findExam(UUID examId) {
        return examRepository.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private Exam findWithParts(UUID examId) {
        return examRepository.findByIdWithParts(examId).orElseThrow(() -> examNotFound(examId));
    }

    private NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("Không tìm thấy đề thi: " + examId);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private ExamResponseDto toStudentDto(Exam exam) {
        return ExamResponseDto.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .durationMinutes(exam.getDurationMinutes())
                .answersRevealed(exam.isAnswersRevealed())
                .parts(exam.getParts().stream().map(part -> ExamResponseDto.ExamPartDto.builder()
                        .id(part.getId())
                        .partName(part.getPartName())
                        .partType(part.getPartType())
                        .passageText(part.getPassageText())
                        .recommendedTimeSeconds(part.getRecommendedTimeSeconds())
                        .orderIndex(part.getOrderIndex())
                        .questions(part.getQuestions().stream().map(q -> ExamResponseDto.QuestionDto.builder()
                                .id(q.getId())
                                .orderIndex(q.getOrderIndex())
                                .content(q.getContent())
                                .optA(q.getOptA())
                                .optB(q.getOptB())
                                .optC(q.getOptC())
                                .optD(q.getOptD())
                                .build()).toList())
                        .build()).toList())
                .build();
    }

    private ExamPreviewResponseDto toPreviewDto(Exam exam) {
        return ExamPreviewResponseDto.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .status(exam.getStatus())
                .durationMinutes(exam.getDurationMinutes())
                .answersRevealed(exam.isAnswersRevealed())
                .createdBy(exam.getCreatedBy())
                .parts(exam.getParts().stream().map(part -> ExamPreviewResponseDto.PartDto.builder()
                        .id(part.getId())
                        .partName(part.getPartName())
                        .partType(part.getPartType())
                        .passageText(part.getPassageText())
                        .recommendedTimeSeconds(part.getRecommendedTimeSeconds())
                        .orderIndex(part.getOrderIndex())
                        .questions(part.getQuestions().stream().map(q -> ExamPreviewResponseDto.QuestionDto.builder()
                                .id(q.getId())
                                .orderIndex(q.getOrderIndex())
                                .content(q.getContent())
                                .optA(q.getOptA())
                                .optB(q.getOptB())
                                .optC(q.getOptC())
                                .optD(q.getOptD())
                                .correctOpt(q.getCorrectOpt())
                                .explanation(q.getExplanation())
                                .relatedVideoId(q.getRelatedVideo() != null ? q.getRelatedVideo().getId() : null)
                                .build()).toList())
                        .build()).toList())
                .build();
    }
}
