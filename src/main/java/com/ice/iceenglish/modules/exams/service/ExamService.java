package com.ice.iceenglish.modules.exams.service;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.ice.iceenglish.core.cache.CacheRegistry;
import com.ice.iceenglish.modules.exams.dto.AnswersRevealedResponse;
import com.ice.iceenglish.modules.exams.dto.ExamImportDto;
import com.ice.iceenglish.modules.exams.dto.ExamImportResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamListPageResponse;
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
import com.ice.iceenglish.core.time.ServerTime;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamPartRepository examPartRepository;
    private final VideoRepository videoRepository;
    private final ExamImportValidator importValidator;
    private final ObjectMapper objectMapper;
    private final CacheRegistry cacheRegistry;
    private final TransactionTemplate readOnlyTx;

    private final Cache<UUID, ExamResponseDto> studentExamCache;
    private final Cache<String, List<ExamResponseDto>> publishedListCache;

    public ExamService(ExamRepository examRepository, ExamPartRepository examPartRepository,
                       VideoRepository videoRepository, ExamImportValidator importValidator,
                       ObjectMapper objectMapper, CacheRegistry cacheRegistry,
                       PlatformTransactionManager transactionManager) {
        this.examRepository = examRepository;
        this.examPartRepository = examPartRepository;
        this.videoRepository = videoRepository;
        this.importValidator = importValidator;
        this.objectMapper = objectMapper;
        this.cacheRegistry = cacheRegistry;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.studentExamCache = cacheRegistry.create(500);
        this.publishedListCache = cacheRegistry.create(8);
    }

    // =====================================================================
    // IMPORT
    // =====================================================================

    /**
     * Import đề từ file JSON giáo viên upload. Đề luôn được tạo ở trạng thái DRAFT;
     * giáo viên xem preview rồi mới publish.
     * answerRevealed luôn được đặt thành false.
     */
    @Transactional
    public ExamImportResponseDto importExam(MultipartFile file, ExamType examType, String createdBy) {
        ExamImportDto dto = parse(file);

        ExamImportValidator.Report report = importValidator.validate(dto);
        List<String> errors = new ArrayList<>(report.errors());
        
        ExamType effectiveExamType = examType;
        if (effectiveExamType == null) {
            errors.add("examType: bắt buộc.");
        }
        
        // Kiểm tra examType chỉ cho phép GENERAL_EXAM hoặc MOCK
        if (effectiveExamType != null && effectiveExamType != ExamType.GENERAL_EXAM && effectiveExamType != ExamType.MOCK) {
            errors.add("examType: chỉ cho phép GENERAL_EXAM hoặc MOCK.");
        }
        
        Integer importedDuration = durationMinutes(dto);
        if (effectiveExamType == ExamType.MOCK && importedDuration == null) {
            errors.add("duration_minutes: bắt buộc khi examType là MOCK.");
        }
        if (effectiveExamType == ExamType.GENERAL_EXAM && importedDuration != null) {
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
                .video(null)
                .answersUrl(null)
                .answersRevealed(false)
                .shuffleParts(dto.getExam() != null
                        && Boolean.TRUE.equals(dto.getExam().getShuffleParts()))
                .createdBy(createdBy)
                .build();

        int totalQuestions = 0;
        for (ExamImportDto.PartImport p : dto.getParts()) {
            ExamPart part = ExamPart.builder()
                    .partType(PartType.parse(p.getPartType()))
                    .partName(p.getPartName().trim())
                    .passageText(blankToNull(p.getPassageText()))
                    .translate(blankToNull(p.getTranslate()))
                    .recommendedTimeSeconds(
                            PartType.parse(p.getPartType()).getStandardTimeSeconds())
                    .orderIndex(p.getOrderIndex())
                    .fixedPosition(Boolean.TRUE.equals(p.getFixedPosition()))
                    .shuffleGroup(p.getShuffleGroup() == null ? null : p.getShuffleGroup().trim())
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
        cacheRegistry.evictAllAfterCommit();

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

    private static String answersUrl(ExamImportDto dto) {
        return !isBlank(dto.getAnswersUrl()) ? dto.getAnswersUrl()
                : dto.getExam() != null ? dto.getExam().getAnswersUrl() : null;
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
     * Đề của học sinh được cache (nội dung bất biến, trạng thái đổi thì cache bị xoá).
     */
    public ExamResponseDto getExamDetail(UUID examId, boolean staff) {
        if (staff) {
            return readOnlyTx.execute(status -> loadExamDetail(examId, true));
        }
        return cacheRegistry.getOrLoad(studentExamCache, examId, () -> loadExamDetail(examId, false));
    }

    private ExamResponseDto loadExamDetail(UUID examId, boolean staff) {
        Exam exam = findWithParts(examId);
        if (!staff && exam.getStatus() != ExamStatus.PUBLISHED) {
            throw examNotFound(examId);
        }

        examPartRepository.fetchQuestionsByExamId(examId);
        return toStudentDto(exam);
    }

    public List<ExamResponseDto> listPublishedExams() {
        return listPublishedExams(null);
    }

    public List<ExamResponseDto> listPublishedExams(ExamType examType) {
        String key = examType == null ? "ALL" : examType.name();
        return cacheRegistry.getOrLoad(publishedListCache, key, () -> loadPublishedExams(examType));
    }

    private List<ExamResponseDto> loadPublishedExams(ExamType examType) {
        List<Exam> exams = examType == null
                ? examRepository.findByStatusOrderByCreatedAtDesc(ExamStatus.PUBLISHED)
                : examRepository.findByStatusAndExamTypeOrderByCreatedAtDesc(
                        ExamStatus.PUBLISHED, examType);
        return exams.stream()
                .map(exam -> ExamResponseDto.builder()
                        .id(exam.getId())
                        .title(exam.getTitle())
                        .examType(exam.getExamType())
                        .status(exam.getStatus())
                        .videoId(exam.getVideo() != null ? exam.getVideo().getId() : null)
                        .durationMinutes(exam.getDurationMinutes())
                        .answersRevealed(exam.isAnswersRevealed())
                        .shuffleParts(exam.isShuffleParts())
                        .parts(List.of())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamResponseDto> listExamsLegacy(
            ExamType examType, ExamStatus status, String keyword, Sort sort) {
        return filterExams(examType, status, keyword, sort)
                .stream()
                .map(exam -> ExamResponseDto.builder()
                        .id(exam.getId())
                        .title(exam.getTitle())
                        .examType(exam.getExamType())
                        .status(exam.getStatus())
                        .videoId(exam.getVideo() != null ? exam.getVideo().getId() : null)
                        .durationMinutes(exam.getDurationMinutes())
                        .answersRevealed(exam.isAnswersRevealed())
                        .shuffleParts(exam.isShuffleParts())
                        .parts(List.of())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Exam> searchExams(
            ExamType examType, ExamStatus status, String keyword, Pageable pageable) {
        List<Exam> matches = filterExams(examType, status, keyword, pageable.getSort());
        int fromIndex = (int) Math.min(pageable.getOffset(), matches.size());
        int toIndex = (int) Math.min((long) fromIndex + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(fromIndex, toIndex), pageable, matches.size());
    }

    private List<Exam> filterExams(
            ExamType examType, ExamStatus status, String keyword, Sort sort) {
        List<Exam> candidates;
        if (examType != null && status != null) {
            candidates = examRepository.findByExamTypeAndStatus(examType, status, sort);
        } else if (examType != null) {
            candidates = examRepository.findByExamType(examType, sort);
        } else if (status != null) {
            candidates = examRepository.findByStatus(status, sort);
        } else {
            candidates = examRepository.findAll(sort);
        }

        String normalizedKeyword = normalizeSearchText(keyword);
        if (normalizedKeyword.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(exam -> normalizeSearchText(exam.getTitle()).contains(normalizedKeyword))
                .toList();
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Transactional(readOnly = true)
    public ExamListPageResponse toExamListPage(
            Page<Exam> exams, Map<UUID, Double> latestScores, Set<UUID> attemptedExamIds) {
        List<UUID> examIds = exams.getContent().stream().map(Exam::getId).toList();
        Map<UUID, Long> questionCounts = examIds.isEmpty()
                ? Map.of()
                : examRepository.findQuestionCountsByExamIds(examIds).stream()
                        .collect(Collectors.toMap(
                                ExamRepository.ExamQuestionCount::getExamId,
                                count -> count.getTotalQuestions() == null ? 0L : count.getTotalQuestions()));

        return ExamListPageResponse.builder()
                .items(exams.getContent().stream()
                        .map(exam -> ExamListPageResponse.Item.builder()
                                .id(exam.getId())
                                .title(exam.getTitle())
                                .mode(exam.getExamType())
                                .status(exam.getStatus())
                                .durationMinutes(exam.getDurationMinutes())
                                .totalQuestions(questionCounts.getOrDefault(exam.getId(), 0L))
                                .createdAt(exam.getCreatedAt() == null ? null
                                        : exam.getCreatedAt().atZone(ServerTime.ZONE_ID).toOffsetDateTime())
                                .attempted(attemptedExamIds == null
                                        ? null : attemptedExamIds.contains(exam.getId()))
                                .latestScore(latestScores == null ? null : latestScores.get(exam.getId()))
                                .build())
                        .toList())
                .page(exams.getNumber())
                .size(exams.getSize())
                .totalElements(exams.getTotalElements())
                .totalPages(exams.getTotalPages())
                .build();
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
        cacheRegistry.evictAllAfterCommit();
    }

    @Transactional
    public void archive(UUID examId) {
        findExam(examId).setStatus(ExamStatus.ARCHIVED);
        cacheRegistry.evictAllAfterCommit();
    }

    @Transactional(readOnly = true)
    public AnswersRevealedResponse getAnswersRevealed(UUID examId) {
        Exam exam = findExam(examId);
        return AnswersRevealedResponse.builder()
                .id(exam.getId())
                .answersRevealed(exam.isAnswersRevealed())
                .build();
    }

    @Transactional
    public AnswersRevealedResponse toggleAnswersRevealed(UUID examId) {
        Exam exam = findExam(examId);
        exam.setAnswersRevealed(!exam.isAnswersRevealed());
        cacheRegistry.evictAllAfterCommit();
        return AnswersRevealedResponse.builder()
                .id(exam.getId())
                .answersRevealed(exam.isAnswersRevealed())
                .build();
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ExamResponseDto toStudentDto(Exam exam) {
        return ExamResponseDto.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .videoId(exam.getVideo() != null ? exam.getVideo().getId() : null)
                .durationMinutes(exam.getDurationMinutes())
                .answersRevealed(exam.isAnswersRevealed())
                .shuffleParts(exam.isShuffleParts())
                .questions(exam.getQuestions().stream().map(q -> ExamResponseDto.QuestionDto.builder()
                        .id(q.getId())
                        .orderIndex(q.getOrderIndex())
                        .content(q.getContent())
                        .optA(q.getOptA())
                        .optB(q.getOptB())
                        .optC(q.getOptC())
                        .optD(q.getOptD())
                        .build()).toList())
                .parts(exam.getParts().stream().map(part -> ExamResponseDto.ExamPartDto.builder()
                        .id(part.getId())
                        .partName(part.getPartName())
                        .partType(part.getPartType())
                        .passageText(part.getPassageText())
                        .recommendedTimeSeconds(part.getPartType().getStandardTimeSeconds())
                        .orderIndex(part.getOrderIndex())
                        .fixedPosition(part.isFixedPosition())
                        .shuffleGroup(part.getShuffleGroup())
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
                .shuffleParts(exam.isShuffleParts())
                .answersUrl(exam.getAnswersUrl())
                .createdBy(exam.getCreatedBy())
                .parts(exam.getParts().stream().map(part -> ExamPreviewResponseDto.PartDto.builder()
                        .id(part.getId())
                        .partName(part.getPartName())
                        .partType(part.getPartType())
                        .passageText(part.getPassageText())
                        .translate(part.getPartType() == PartType.READING_8
                                || part.getPartType() == PartType.READING_10
                                ? part.getTranslate() : null)
                        .recommendedTimeSeconds(part.getPartType().getStandardTimeSeconds())
                        .orderIndex(part.getOrderIndex())
                        .fixedPosition(part.isFixedPosition())
                        .shuffleGroup(part.getShuffleGroup())
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
