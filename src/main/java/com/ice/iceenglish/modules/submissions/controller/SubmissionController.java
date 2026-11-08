package com.ice.iceenglish.modules.submissions.controller;

import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.HomeworkReviewResponse;
import com.ice.iceenglish.modules.submissions.dto.AnswerUrlResponse;
import com.ice.iceenglish.modules.submissions.dto.PracticeHistoryResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScoreResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScorePageResponse;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

        private final SubmissionService submissionService;

        @GetMapping("/practice/{examId}/latest-score")
        public ResponseEntity<SubmissionScoreResponse> getLatestPracticeScore(
                        @PathVariable UUID examId, Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.getLatestPracticeScore(examId, authentication.getName()));
        }

        @GetMapping("/practice/{examId}/history")
        public ResponseEntity<PracticeHistoryResponse> getPracticeHistory(
                        @PathVariable UUID examId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.getPracticeHistory(
                                                examId, authentication.getName(), page, size));
        }

        @GetMapping("/practice/{submissionId}/review")
        public ResponseEntity<ExamResultResponse> reviewPracticeSubmission(
                        @PathVariable UUID submissionId,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.reviewPracticeSubmission(
                                                submissionId, authentication.getName()));
        }

        @GetMapping("/practice/{examId}/latest-review")
        public ResponseEntity<ExamResultResponse> getLatestPracticeReview(
                        @PathVariable UUID examId, Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.reviewLatestPractice(examId, authentication.getName()));
        }

        @GetMapping("/homework/{examId}/latest-review")
        public ResponseEntity<HomeworkReviewResponse> getLatestHomeworkReview(
                        @PathVariable UUID examId, Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.reviewLatestHomework(examId, authentication.getName()));
        }

        @GetMapping("/mock/scores")
        public ResponseEntity<?> getMockScores(
                        @RequestParam(value = "page", required = false) Integer page,
                        @RequestParam(value = "size", required = false) Integer size,
                        @RequestParam(value = "sort", defaultValue = "submittedAt,desc") String sort,
                        Authentication authentication) {
                if (page == null && size == null) {
                        return ResponseEntity.ok(
                                        submissionService.getMockScores(authentication.getName()));
                }
                int pageNumber = page == null ? 0 : page;
                int pageSize = size == null ? 20 : size;
                if (pageNumber < 0) {
                        throw new IllegalArgumentException("page phải >= 0.");
                }
                if (pageSize < 1 || pageSize > 100) {
                        throw new IllegalArgumentException("size phải nằm trong khoảng 1 đến 100.");
                }
                String[] sortParts = sort.split(",", -1);
                if (sortParts.length != 2 || !"submittedAt".equals(sortParts[0])) {
                        throw new IllegalArgumentException("sort chỉ hỗ trợ submittedAt.");
                }
                Sort.Direction direction;
                try {
                        direction = Sort.Direction.fromString(sortParts[1]);
                } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("sort direction phải là asc hoặc desc.");
                }
                Sort pageableSort = Sort.by(new Sort.Order(direction, "submittedAt"))
                                .and(Sort.by(Sort.Order.desc("id")));
                Page<SubmissionScoreResponse> scores = submissionService.getMockScoresPage(
                                authentication.getName(),
                                PageRequest.of(pageNumber, pageSize, pageableSort));
                return ResponseEntity.ok(SubmissionScorePageResponse.builder()
                                .items(scores.getContent())
                                .page(scores.getNumber())
                                .size(scores.getSize())
                                .totalElements(scores.getTotalElements())
                                .totalPages(scores.getTotalPages())
                                .build());
        }

        @GetMapping("/exams/{examId}/answer-url")
        public ResponseEntity<AnswerUrlResponse> getAnswerUrl(
                        @PathVariable UUID examId,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.getAnswerUrl(examId, authentication.getName()));
        }

        @GetMapping("/mock/{examId}/review")
        public ResponseEntity<ExamResultResponse> reviewMockExam(
                        @PathVariable UUID examId,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.reviewMockExam(
                                                examId, authentication.getName()));
        }

        /**
         * Chế độ và thời lượng được lấy từ exam trong DB, không nhận từ học sinh.
         */
        @PostMapping("/start/{examId}")
        public ResponseEntity<StartExamResponse> startExam(
                        @PathVariable UUID examId,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.startExam(examId, authentication.getName()));
        }

        @PostMapping("/homework/start/{videoId}")
        public ResponseEntity<StartExamResponse> startHomework(
                                @PathVariable UUID videoId,
                                Authentication authentication) {
                return ResponseEntity.ok(
                                        submissionService.startHomeworkByVideo(videoId, authentication.getName()));
        }

        /**
         * Dùng chung cho luyện đề và thi thử. Body có submissionId, exam_id,
         * answers. Thời gian từng part được đo phía server qua endpoint chuyển part.
         * Điểm trả về bằng totalCorrect * 0.25.
         */
        @PostMapping("/submit")
        public ResponseEntity<ExamResultResponse> submitExam(
                        @RequestBody SubmitExamRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.submitExam(request, authentication.getName()));
        }

        @PostMapping("/homework/submit")
        public ResponseEntity<ExamResultResponse> submitHomework(
                                @RequestBody SubmitExamRequest request,
                                Authentication authentication) {
                return ResponseEntity.ok(
                                        submissionService.submitHomework(request, authentication.getName()));
        }

        @PutMapping("/{submissionId}/parts/{partId}/time")
        public ResponseEntity<Void> trackPartTime(
                        @PathVariable UUID submissionId,
                        @PathVariable UUID partId,
                        Authentication authentication) {
                submissionService.trackPartTime(
                                submissionId, partId, authentication.getName());
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/{submissionId}/part-logs")
        public ResponseEntity<List<ExamResultResponse.PartTimeLogDto>> getPartTimeLogs(
                        @PathVariable UUID submissionId,
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.getPartTimeLogs(submissionId, authentication.getName()));
        }
}
