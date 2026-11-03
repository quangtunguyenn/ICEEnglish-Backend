package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * Lấy toàn bộ video hoặc tìm kiếm theo một phần title.
     * GET /api/v1/videos?title=...
     */
    @GetMapping
    public ResponseEntity<List<VideoResponseDto>> getVideos(
            @RequestParam(value = "title", required = false) String title) {
        return ResponseEntity.ok(videoService.getAll(title));
    }

    /** Lấy danh sách title duy nhất của toàn bộ video. */
    @GetMapping("/titles")
    public ResponseEntity<List<String>> getAllVideoTitles() {
        return ResponseEntity.ok(videoService.getAllTitles());
    }

    /** Endpoint tường minh cho client cần tìm video theo title. */
    @GetMapping("/title/{title}")
    public ResponseEntity<List<VideoResponseDto>> getVideosByTitle(@PathVariable String title) {
        return ResponseEntity.ok(videoService.findByTitle(title));
    }
}
