package com.ice.iceenglish.modules.learning.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class CreateVideoLearningBundleResponse {
    UUID videoId;
    UUID homeworkId;
    int vocabularyCount;
}
