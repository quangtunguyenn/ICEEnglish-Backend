package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.modules.learning.dto.CreateVideoLearningBundleResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.dto.VideoTitleResponse;
import com.ice.iceenglish.modules.videos.dto.HomeworkAnswersResponse;
import com.ice.iceenglish.modules.videos.dto.VideoDocumentResponse;
import com.ice.iceenglish.modules.videos.dto.VideoLearningContentResponse;
import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import com.ice.iceenglish.modules.videos.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * Tạo một bài học gồm video, homework và danh sách vocabulary trong một transaction.
     * Nhận file JSON (multipart/form-data, field "file"). Homework được lưu thành một part GRAMMAR.
     */
    @PostMapping(value = "/learning-bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')")
    public ResponseEntity<CreateVideoLearningBundleResponse> createLearningBundle(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(videoService.createLearningBundle(file, authentication.getName()));
    }

    /**
     * Lấy toàn bộ video hoặc tìm kiếm theo một phần title.
     * GET /api/v1/videos?title=...
     */
    @GetMapping
    public ResponseEntity<List<VideoResponseDto>> getVideos(
            @RequestParam(value = "title", required = false) String title) {
        return ResponseEntity.ok(videoService.getAll(title));
    }

    /** Lấy video_id và title của toàn bộ video, sắp xếp theo title. */
    @GetMapping("/titles")
    public ResponseEntity<List<VideoTitleResponse>> getAllVideoTitles() {
        return ResponseEntity.ok(videoService.getAllTitles());
    }

    @GetMapping("/{videoId}/learning-content")
    public ResponseEntity<VideoLearningContentResponse> getLearningContent(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getLearningContent(videoId));
    }

    @GetMapping("/{videoId}/document")
    public ResponseEntity<VideoDocumentResponse> getDocument(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getDocument(videoId));
    }

    @GetMapping("/{videoId}/homework")
    public ResponseEntity<ExamResponseDto> getHomework(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getHomework(videoId));
    }

    @GetMapping("/{videoId}/homework/answers")
    public ResponseEntity<HomeworkAnswersResponse> getHomeworkAnswers(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getHomeworkAnswers(videoId));
    }

    @GetMapping("/{videoId}/vocabularies")
    public ResponseEntity<List<VocabularyResponseDto>> getVocabularies(@PathVariable UUID videoId) {
        return ResponseEntity.ok(videoService.getVocabularies(videoId));
    }
}
