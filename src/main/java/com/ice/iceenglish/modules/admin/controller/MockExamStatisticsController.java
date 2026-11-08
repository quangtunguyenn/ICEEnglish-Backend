package com.ice.iceenglish.modules.admin.controller;

import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.ExamPage;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Overview;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.QuestionStats;
import com.ice.iceenglish.modules.admin.dto.MockExamStatisticsResponse.Top;
import com.ice.iceenglish.modules.admin.service.MockExamStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/mock-exams")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')")
public class MockExamStatisticsController {

    private final MockExamStatisticsService statisticsService;

    @GetMapping
    public ResponseEntity<ExamPage> listPublishedExams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return ResponseEntity.ok(statisticsService.listPublishedExams(page, size, keyword));
    }

    @GetMapping("/{examId}/stats/overview")
    public ResponseEntity<Overview> overview(
            @PathVariable UUID examId,
            @RequestParam(required = false) String buckets) {
        return ResponseEntity.ok(statisticsService.overview(examId, buckets));
    }

    @GetMapping("/{examId}/stats/questions")
    public ResponseEntity<QuestionStats> questions(@PathVariable UUID examId) {
        return ResponseEntity.ok(statisticsService.questions(examId));
    }

    @GetMapping("/{examId}/stats/top")
    public ResponseEntity<Top> top(
            @PathVariable UUID examId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(statisticsService.top(examId, limit));
    }
}
