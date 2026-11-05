package com.ice.iceenglish.modules.exams.controller;

import com.ice.iceenglish.modules.exams.dto.AnswersRevealedResponse;
import com.ice.iceenglish.modules.exams.dto.ExamImportResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamPreviewResponseDto;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.exams.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
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

    /**
     * Danh sách đề PUBLISHED; có thể lọc bằng mode=GENERAL_EXAM, MOCK hoặc HOMEWORK.
     * Ví dụ: GET /api/v1/exams?mode=MOCK
     */
    @GetMapping
    public ResponseEntity<java.util.List<ExamResponseDto>> listPublishedExams(
            @RequestParam(value = "mode", required = false) ExamType mode) {
        return ResponseEntity.ok(examService.listPublishedExams(mode));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamResponseDto> getExamDetail(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(examService.getExamDetail(id, isStaff(authentication)));
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
}
