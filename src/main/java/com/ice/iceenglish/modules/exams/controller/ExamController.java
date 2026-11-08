package com.ice.iceenglish.modules.exams.controller;

import com.ice.iceenglish.modules.exams.dto.AnswersRevealedResponse;
import com.ice.iceenglish.modules.exams.dto.ExamImportResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamListPageResponse;
import com.ice.iceenglish.modules.exams.dto.ExamPreviewResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.service.ExamService;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import com.ice.iceenglish.modules.submissions.service.PartOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamController {

    /** Chấp nhận cả dạng "TEACHER" lẫn "ROLE_TEACHER" tùy cách nạp authority của JwtFilter. */
    private static final String STAFF_ONLY =
            "hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')";
    private static final Set<String> STAFF_AUTHORITIES =
            Set.of("ADMIN", "TEACHER", "ROLE_ADMIN", "ROLE_TEACHER");

    private final ExamService examService;
    private final SubmissionService submissionService;

    /**
     * Import đề từ file JSON (multipart/form-data).
     * Fields: file (bắt buộc), examType (GENERAL_EXAM | MOCK, bắt buộc).
     * Video ôn tập của từng câu được lấy từ related_video_id trong JSON.
     * answerRevealed luôn mặc định false.
     *
     * Với MOCK, duration_minutes trong JSON là thời lượng thi bắt buộc.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STAFF_ONLY)
    public ResponseEntity<ExamImportResponseDto> importExam(
            @RequestParam("file") MultipartFile file,
            @RequestParam("examType") ExamType examType,
            Authentication authentication) {

        ExamImportResponseDto result =
                examService.importExam(file, examType, authentication.getName());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/exams/{id}/preview")
                .buildAndExpand(result.getId())
                .toUri();
        return ResponseEntity.created(location).body(result);
    }

    /** Danh sách đề; học sinh luôn chỉ xem đề PUBLISHED. */
    @GetMapping
    public ResponseEntity<?> listPublishedExams(
            @RequestParam(value = "mode", required = false) ExamType mode,
            @RequestParam(value = "status", required = false) String requestedStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {
        boolean staff = isStaff(authentication);
        Sort pageableSort = parseExamSort(sort);
        ExamStatus status = staff ? parseExamStatus(requestedStatus) : ExamStatus.PUBLISHED;
        if (page == null && size == null) {
            List<ExamResponseDto> exams = examService.listExamsLegacy(
                    mode, status, keyword, pageableSort);
            Set<UUID> practiceExamIds = exams.stream()
                    .filter(exam -> exam.getExamType() == ExamType.GENERAL_EXAM)
                    .map(ExamResponseDto::getId)
                    .collect(Collectors.toSet());
            Set<UUID> submittedPracticeExamIds = isStudent(authentication)
                    ? submissionService.getSubmittedPracticeExamIds(
                            authentication.getName(), practiceExamIds)
                    : Set.of();
            return ResponseEntity.ok(exams.stream()
                    .map(exam -> exam.toBuilder()
                            .hasSubmitted(exam.getExamType() == ExamType.GENERAL_EXAM
                                    ? submittedPracticeExamIds.contains(exam.getId())
                                    : null)
                            .build())
                    .toList());
        }

        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 12 : size;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page phải >= 0.");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("size phải nằm trong khoảng 1 đến 100.");
        }

        var pageable = PageRequest.of(pageNumber, pageSize, pageableSort);
        var exams = examService.searchExams(
                mode, status, keyword, pageable);
        Map<UUID, Double> latestScores = null;
        Set<UUID> attemptedExamIds = null;
        if (isStudent(authentication)) {
            latestScores = new HashMap<>();
            attemptedExamIds = new HashSet<>();
            for (ExamType scoreMode : List.of(
                    ExamType.GENERAL_EXAM, ExamType.MOCK, ExamType.HOMEWORK)) {
                List<UUID> ids = exams.getContent().stream()
                        .filter(exam -> exam.getExamType() == scoreMode)
                        .map(com.ice.iceenglish.modules.exams.entity.Exam::getId)
                        .toList();
                if (ids.isEmpty()) {
                    continue;
                }
                attemptedExamIds.addAll(submissionService.getAttemptedExamIds(
                        authentication.getName(), scoreMode.name(), ids));
                latestScores.putAll(submissionService.getLatestScoresByExamIds(
                        authentication.getName(), scoreMode.name(), ids));
            }
        }
        ExamListPageResponse response = examService.toExamListPage(
                exams, latestScores, attemptedExamIds);
        return ResponseEntity.ok(response);
    }

    private Sort parseExamSort(String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("createdAt", "title").contains(parts[0])) {
            throw new IllegalArgumentException("sort chỉ hỗ trợ createdAt hoặc title.");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sort direction phải là asc hoặc desc.");
        }
        return Sort.by(new Sort.Order(direction, parts[0]))
                .and(Sort.by(Sort.Order.desc("id")));
    }

    private ExamStatus parseExamStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ExamStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status phải là PUBLISHED, DRAFT hoặc ARCHIVED.");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamResponseDto> getExamDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID submissionId,
            Authentication authentication) {
        List<UUID> order = submissionId == null ? null
                : submissionService.getOwnedPartOrder(id, submissionId, authentication.getName());
        ExamResponseDto exam = examService.getExamDetail(id, isStaff(authentication));
        if (order == null) {
            return ResponseEntity.ok(exam);
        }
        Map<UUID, ExamResponseDto.ExamPartDto> parts = exam.getParts().stream()
                .collect(Collectors.toMap(ExamResponseDto.ExamPartDto::getId, Function.identity()));
        PartOrder.resolve(order, exam.getParts().stream().map(ExamResponseDto.ExamPartDto::getId).toList());
        Map<UUID, Integer> indexes = PartOrder.indexes(order);
        int[] number = {0};
        List<ExamResponseDto.ExamPartDto> orderedParts = order.stream().map(parts::get)
                .map(part -> part.toBuilder()
                        .displayIndex(indexes.get(part.getId()))
                        .questions(part.getQuestions().stream()
                                .map(question -> question.toBuilder()
                                        .displayNumber(++number[0]).build())
                                .toList())
                        .build())
                .toList();
        List<ExamResponseDto.QuestionDto> questions = exam.getQuestions().stream()
                .map(question -> question.toBuilder().displayNumber(++number[0]).build())
                .toList();
        return ResponseEntity.ok(exam.toBuilder().parts(orderedParts).questions(questions).build());
    }

    /** Đề kèm đáp án và lời giải, để giáo viên duyệt trước khi publish. */
    @GetMapping("/{id}/preview")
    @PreAuthorize(STAFF_ONLY)
    public ResponseEntity<ExamPreviewResponseDto> getExamPreview(@PathVariable UUID id) {
        return ResponseEntity.ok(examService.getExamPreview(id));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize(STAFF_ONLY)
    public ResponseEntity<Void> publish(@PathVariable UUID id) {
        examService.publish(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize(STAFF_ONLY)
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        examService.archive(id);
        return ResponseEntity.noContent().build();
    }

    /** Lấy trạng thái hiển thị đáp án (is_answers_revealed) của exam. */
    @GetMapping("/{id}/answers-revealed")
    public ResponseEntity<AnswersRevealedResponse> getAnswersRevealed(@PathVariable UUID id) {
        return ResponseEntity.ok(examService.getAnswersRevealed(id));
    }

    /** Đảo trạng thái hiển thị đáp án (is_answers_revealed) và trả về trạng thái mới. */
    @PatchMapping("/{id}/answers-revealed")
    @PreAuthorize(STAFF_ONLY)
    public ResponseEntity<AnswersRevealedResponse> toggleAnswersRevealed(@PathVariable UUID id) {
        return ResponseEntity.ok(examService.toggleAnswersRevealed(id));
    }

    private boolean isStaff(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_AUTHORITIES::contains);
    }

    private boolean isStudent(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "STUDENT".equals(authority)
                        || "ROLE_STUDENT".equals(authority));
    }
}
