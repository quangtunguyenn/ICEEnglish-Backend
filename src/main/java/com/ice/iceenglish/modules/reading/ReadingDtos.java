package com.ice.iceenglish.modules.reading;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReadingDtos {
    private ReadingDtos() {}

    public record ImportFile(@JsonProperty("schema_version") Integer schemaVersion, BlogInput blog,
            @JsonProperty("passage_blocks") List<Block> passageBlocks,
            List<VocabularyInput> vocabularies, List<QuestionInput> questions) {}
    public record BlogInput(String title, TopicInput topic,
            @JsonProperty("content_type") String contentType, String difficulty, String summary,
            @JsonProperty("estimated_reading_minutes") Integer estimatedReadingMinutes,
            @JsonProperty("guest_accessible") Boolean guestAccessible,
            @JsonProperty("cover_image_url") String coverImageUrl,
            @JsonProperty("source_name") String sourceName, @JsonProperty("source_url") String sourceUrl) {}
    public record TopicInput(String slug, String name) {}
    public record Block(String id, String text, String translation) {}
    public record VocabularyInput(@JsonProperty("order_index") Integer orderIndex, String word,
            String phonetic, String meaning, @JsonProperty("example_sentence") String exampleSentence,
            @JsonProperty("example_translation") String exampleTranslation,
            @JsonProperty("passage_block_id") String passageBlockId) {}
    public record QuestionInput(@JsonProperty("order_index") Integer orderIndex, String content,
            @JsonProperty("skill_type") String skillType,
            @JsonProperty("evidence_block_ids") List<String> evidenceBlockIds,
            String explanation, List<AnswerInput> answers) {}
    public record AnswerInput(@JsonProperty("option_key") String optionKey, String content,
            @JsonProperty("is_correct") Boolean isCorrect, String explanation) {}

    public record Topic(String slug, String name) {}
    public record ListItem(UUID id, String title, Topic topic, String summary, String contentType,
            String difficulty, Integer estimatedReadingMinutes, String coverImageUrl,
            boolean accessible, String status, boolean guestAccessible, OffsetDateTime publishedAt) {}
    public record Page(List<ListItem> items, int page, int size, long totalElements, long totalPages) {}
    public record Answer(UUID id, String optionKey, String content) {}
    public record Question(UUID id, int orderIndex, String content, String skillType, List<Answer> answers) {}
    public record Vocabulary(UUID id, int orderIndex, String word, String phonetic, String meaning,
            String exampleSentence, String exampleTranslation, String passageBlockId) {}
    public record Detail(ListItem blog, int revision, List<Block> passageBlocks,
            List<Question> questions, List<Vocabulary> vocabularies, String sourceName, String sourceUrl) {}
    public record AdminAnswer(UUID id, String optionKey, String content, boolean correct, String explanation) {}
    public record AdminQuestion(UUID id, int orderIndex, String content, String skillType,
            List<String> evidenceBlockIds, String explanation, List<AdminAnswer> answers) {}
    public record AdminDetail(ListItem blog, int revision, List<Block> passageBlocks,
            List<AdminQuestion> questions, List<Vocabulary> vocabularies, String sourceName,
            String sourceUrl, String createdBy) {}
    public record AnswerRequest(Integer revision, String selectedOption) {}
    public record AnswerResult(UUID questionId, String selectedOption, boolean isCorrect,
            String optionExplanation, String questionExplanation, List<String> evidenceBlockIds) {}
    public record GuestAccessRequest(Boolean guestAccessible) {}
    public record ImportResult(UUID id, String status, int revision) {}
}
