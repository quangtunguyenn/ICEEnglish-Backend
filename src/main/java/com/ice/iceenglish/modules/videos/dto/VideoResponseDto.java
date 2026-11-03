package com.ice.iceenglish.modules.videos.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class VideoResponseDto {
    UUID id;
    String title;
    String videoUrl;
    String documentUrl;
    LocalDateTime createdAt;
}
