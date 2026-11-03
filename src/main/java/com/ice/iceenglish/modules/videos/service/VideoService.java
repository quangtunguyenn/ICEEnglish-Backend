package com.ice.iceenglish.modules.videos.service;

import com.ice.iceenglish.modules.videos.dto.VideoResponseDto;
import com.ice.iceenglish.modules.videos.entity.Video;
import com.ice.iceenglish.modules.videos.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    @Transactional(readOnly = true)
    public List<VideoResponseDto> getAll(String title) {
        List<Video> videos = title == null || title.isBlank()
                ? videoRepository.findAll()
                : videoRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(title.trim());
        return videos.stream().map(VideoService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<VideoResponseDto> findByTitle(String title) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return videoRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(title.trim())
                .stream()
                .map(VideoService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAllTitles() {
        return videoRepository.findAllTitles();
    }

    private static VideoResponseDto toDto(Video video) {
        return VideoResponseDto.builder()
                .id(video.getId())
                .title(video.getTitle())
                .videoUrl(video.getVideoUrl())
                .documentUrl(video.getDocumentUrl())
                .createdAt(video.getCreatedAt())
                .build();
    }
}
