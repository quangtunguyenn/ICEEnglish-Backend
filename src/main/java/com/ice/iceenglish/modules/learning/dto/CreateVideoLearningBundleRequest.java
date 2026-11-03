package com.ice.iceenglish.modules.learning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateVideoLearningBundleRequest {

    private VideoInfo video;
    private HomeworkInfo homework;
    private List<VocabularyInfo> vocabularies;

    @Data
    public static class VideoInfo {
        private String title;
        private String videoUrl;
        private String documentUrl;
    }

    @Data
    public static class HomeworkInfo {
        private String title;
        private List<QuestionInfo> questions;
    }

    @Data
    public static class QuestionInfo {
        private Integer orderIndex;
        private String content;
        private Map<String, String> options;

        @JsonProperty("correctOption")
        @JsonAlias("correct_option")
        private String correctOption;

        private String explanation;
    }

    @Data
    public static class VocabularyInfo {
        private String word;
        private String meaning;
    }
}
