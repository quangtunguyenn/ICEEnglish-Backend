package com.ice.iceenglish.modules.guest;

import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.submissions.dto.ExamResultResponse;
import com.ice.iceenglish.modules.submissions.dto.AnswerUrlResponse;
import com.ice.iceenglish.modules.submissions.dto.StartExamResponse;
import com.ice.iceenglish.modules.submissions.dto.SubmitExamRequest;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guest")
@RequiredArgsConstructor
public class GuestController {
    private final GuestService service;

    @GetMapping("/exams")
    public List<GuestService.GuestExam> exams(@RequestParam ExamType mode) {
        return service.listExams(mode);
    }

    @PostMapping("/exams/{examId}/start")
    public StartExamResponse start(@PathVariable UUID examId,
            @RequestHeader(value = "X-Guest-Attempt", required = false) UUID token) {
        return service.startExam(examId, token);
    }

    @GetMapping("/exams/{examId}")
    public ExamResponseDto exam(@PathVariable UUID examId,
            @RequestHeader("X-Guest-Attempt") UUID token) {
        return service.examDetail(examId, token);
    }

    @PutMapping("/exams/{examId}/parts/{partId}/time")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void track(@PathVariable UUID examId, @PathVariable UUID partId,
            @RequestHeader("X-Guest-Attempt") UUID token) {
        service.track(examId, partId, token);
    }

    @PostMapping("/exams/{examId}/submit")
    public ExamResultResponse submit(@PathVariable UUID examId,
            @RequestHeader("X-Guest-Attempt") UUID token, @RequestBody SubmitExamRequest request) {
        return service.submitExam(examId, token, request);
    }

    @GetMapping("/exams/{examId}/part-logs")
    public List<ExamResultResponse.PartTimeLogDto> partLogs(@PathVariable UUID examId,
            @RequestHeader("X-Guest-Attempt") UUID token) {
        return service.partLogs(examId, token);
    }

    @GetMapping("/exams/{examId}/answer-url")
    public AnswerUrlResponse answerUrl(@PathVariable UUID examId,
            @RequestHeader("X-Guest-Attempt") UUID token) {
        return service.answerUrl(examId, token);
    }

    @GetMapping("/videos")
    public List<GuestService.GuestVideo> videos() {
        return service.listVideos();
    }

    @GetMapping("/videos/{videoId}/learning-content")
    public VideoLearningContentResponse learningContent(@PathVariable UUID videoId) {
        return service.learningContent(videoId);
    }

    @GetMapping("/videos/{videoId}/vocabularies")
    public List<VocabularyResponseDto> vocabularies(@PathVariable UUID videoId) {
        return service.vocabularies(videoId);
    }

    @PostMapping("/videos/{videoId}/homework/start")
    public StartExamResponse startHomework(@PathVariable UUID videoId,
            @RequestHeader(value = "X-Guest-Attempt", required = false) UUID token) {
        return service.startHomework(videoId, token);
    }

    @PostMapping("/videos/{videoId}/homework/submit")
    public ExamResultResponse submitHomework(@PathVariable UUID videoId,
            @RequestHeader("X-Guest-Attempt") UUID token, @RequestBody SubmitExamRequest request) {
        return service.submitHomework(videoId, token, request);
    }

    @GetMapping("/videos/{videoId}/homework/answers")
    public HomeworkAnswersResponse homeworkAnswers(@PathVariable UUID videoId,
            @RequestHeader("X-Guest-Attempt") UUID token) {
        return service.homeworkAnswers(videoId, token);
    }
}
