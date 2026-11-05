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
        @JsonProperty("answers_url")
        @JsonAlias("answersUrl")
        private String answersUrl;
        private String documentUrl;
    }

    @Data
    public static class HomeworkInfo {
        private String title;
        @JsonProperty("answers_url")
        @JsonAlias("answersUrl")
        private String answersUrl;
        private ExamInfo exam;
        private List<QuestionInfo> questions;

        public String getTitle() {
            return title != null ? title : exam != null ? exam.getTitle() : null;
        }

        public String getAnswersUrl() {
            return answersUrl != null ? answersUrl : exam != null ? exam.getAnswersUrl() : null;
        }
    }

    @Data
    public static class ExamInfo {
        private String title;
        @JsonProperty("answers_url")
        @JsonAlias("answersUrl")
        private String answersUrl;
    }

    @Data
    public static class QuestionInfo {
        private Integer orderIndex;
        private String content;
        private Map<String, String> options;
        private String optA;
        private String optB;
        private String optC;
        private String optD;

        @JsonProperty("correctOption")
        @JsonAlias({"correct_option", "correctOpt", "correct_opt"})
        private String correctOption;

        public Map<String, String> getOptions() {
            if (options != null) {
                return options;
            }
            Map<String, String> flat = new java.util.HashMap<>();
            flat.put("A", optA);
            flat.put("B", optB);
            flat.put("C", optC);
            flat.put("D", optD);
            return flat;
        }

        private String explanation;
    }

    @Data
    public static class VocabularyInfo {
        private String word;
        private String meaning;
    }
}
