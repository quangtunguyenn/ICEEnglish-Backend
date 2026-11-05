package com.ice.iceenglish.modules.videos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class VideoTitleResponse {
    @JsonProperty("video_id")
    UUID videoId;
    String title;
}
