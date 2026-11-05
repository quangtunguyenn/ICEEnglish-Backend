package com.ice.iceenglish.modules.videos.dto;

import com.ice.iceenglish.modules.exams.dto.ExamResponseDto;
import com.ice.iceenglish.modules.learning.dto.VocabularyResponseDto;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class VideoLearningContentResponse {
    UUID videoId;
    String title;
    String videoUrl;
    String documentUrl;
    ExamResponseDto homework;
    List<VocabularyResponseDto> vocabularies;
}
