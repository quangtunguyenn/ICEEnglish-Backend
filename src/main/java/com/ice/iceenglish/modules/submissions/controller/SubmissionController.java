package com.ice.iceenglish.modules.submissions.controller;

import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.HomeworkReviewResponse;
import com.ice.iceenglish.modules.submissions.dto.AnswerUrlResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.TrackPartTimeRequest;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.submissions.dto.SubmissionScoreResponse;
import com.ice.iceenglish.modules.submissions.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        public ResponseEntity<List<SubmissionScoreResponse>> getMockScores(
                        Authentication authentication) {
                return ResponseEntity.ok(
                                submissionService.getMockScores(authentication.getName()));
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
         * answers và có thể có partLogs; partLogs chỉ được lưu cho MOCK.
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
                        @RequestBody TrackPartTimeRequest request,
                        Authentication authentication) {
                submissionService.trackPartTime(
                                submissionId, partId, request, authentication.getName());
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
