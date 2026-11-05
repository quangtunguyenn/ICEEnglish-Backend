package com.ice.iceenglish.modules.videos.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class VideoDocumentResponse {
    UUID videoId;
    String documentUrl;
}
