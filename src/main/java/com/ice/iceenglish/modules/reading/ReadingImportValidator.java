package com.ice.iceenglish.modules.reading;

import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ReadingImportValidator {
    private static final Set<String> TYPES = Set.of("ARTICLE", "MAGAZINE", "STORY", "PASSAGE");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> SKILLS = Set.of(
            "SKIMMING", "SCANNING", "INFERENCE", "VOCABULARY_IN_CONTEXT", "REFERENCE");
    private static final Set<String> OPTIONS = Set.of("A", "B", "C", "D");

    public void validate(ImportFile file) {
        require(file != null, "File JSON không được rỗng.");
        require(Integer.valueOf(1).equals(file.schemaVersion()), "schema_version phải bằng 1.");
        BlogInput b = file.blog();
        require(b != null, "blog là bắt buộc.");
        text(b.title(), 250, "blog.title", true);
        text(b.summary(), 3000, "blog.summary", true);
        require(b.topic() != null, "blog.topic là bắt buộc.");
        text(b.topic().slug(), 80, "blog.topic.slug", true);
        require(b.topic().slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*"), "blog.topic.slug không hợp lệ.");
        text(b.topic().name(), 150, "blog.topic.name", true);
        require(b.contentType() != null && TYPES.contains(b.contentType()), "blog.content_type không hợp lệ.");
        require(b.difficulty() != null && DIFFICULTIES.contains(b.difficulty()), "blog.difficulty không hợp lệ.");
        require(b.guestAccessible() != null, "blog.guest_accessible là bắt buộc.");
        require(b.estimatedReadingMinutes() == null || b.estimatedReadingMinutes() > 0,
                "blog.estimated_reading_minutes phải lớn hơn 0.");
        url(b.coverImageUrl(), "blog.cover_image_url");
        url(b.sourceUrl(), "blog.source_url");
        text(b.sourceName(), 250, "blog.source_name", false);

        list(file.passageBlocks(), 1, 200, "passage_blocks");
        Set<String> blocks = new HashSet<>();
        for (int i = 0; i < file.passageBlocks().size(); i++) {
            Block block = file.passageBlocks().get(i);
            String path = "passage_blocks[" + i + "]";
            require(block != null, path + " không được null.");
            text(block.id(), 80, path + ".id", true);
            require(blocks.add(block.id()), path + ".id bị trùng.");
            text(block.text(), 10000, path + ".text", true);
            text(block.translation(), 10000, path + ".translation", false);
        }
        list(file.vocabularies(), 0, 500, "vocabularies");
        Set<Integer> vocabOrders = new HashSet<>();
        for (int i = 0; i < file.vocabularies().size(); i++) {
            VocabularyInput v = file.vocabularies().get(i);
            String path = "vocabularies[" + i + "]";
            require(v != null, path + " không được null.");
            order(v.orderIndex(), vocabOrders, path);
            text(v.word(), 150, path + ".word", true);
            text(v.phonetic(), 250, path + ".phonetic", false);
            text(v.meaning(), 3000, path + ".meaning", true);
            text(v.exampleSentence(), 3000, path + ".example_sentence", false);
            text(v.exampleTranslation(), 3000, path + ".example_translation", false);
            require(v.passageBlockId() == null || blocks.contains(v.passageBlockId()),
                    path + ".passage_block_id không tồn tại.");
        }
        list(file.questions(), 1, 200, "questions");
        Set<Integer> questionOrders = new HashSet<>();
        for (int i = 0; i < file.questions().size(); i++) {
            QuestionInput q = file.questions().get(i);
            String path = "questions[" + i + "]";
            require(q != null, path + " không được null.");
            order(q.orderIndex(), questionOrders, path);
            text(q.content(), 5000, path + ".content", true);
            text(q.explanation(), 5000, path + ".explanation", true);
            require(q.skillType() != null && SKILLS.contains(q.skillType()), path + ".skill_type không hợp lệ.");
            list(q.evidenceBlockIds(), 1, 200, path + ".evidence_block_ids");
            require(new HashSet<>(q.evidenceBlockIds()).size() == q.evidenceBlockIds().size()
                    && blocks.containsAll(q.evidenceBlockIds()), path + ".evidence_block_ids không hợp lệ.");
            list(q.answers(), 4, 4, path + ".answers");
            Set<String> options = new HashSet<>();
            int correct = 0;
            for (int j = 0; j < q.answers().size(); j++) {
                AnswerInput a = q.answers().get(j);
                String answerPath = path + ".answers[" + j + "]";
                require(a != null, answerPath + " không được null.");
                require(a.optionKey() != null && OPTIONS.contains(a.optionKey())
                        && options.add(a.optionKey()), answerPath + ".option_key không hợp lệ hoặc trùng.");
                text(a.content(), 5000, answerPath + ".content", true);
                text(a.explanation(), 5000, answerPath + ".explanation", false);
                require(a.isCorrect() != null, answerPath + ".is_correct là bắt buộc.");
                if (a.isCorrect()) correct++;
            }
            require(correct == 1, path + ".answers phải có đúng một đáp án đúng.");
        }
    }

    private static void order(Integer value, Set<Integer> seen, String path) {
        require(value != null && value > 0 && seen.add(value), path + ".order_index không hợp lệ hoặc trùng.");
    }

    private static void list(List<?> value, int min, int max, String path) {
        require(value != null && value.size() >= min && value.size() <= max,
                path + " phải có từ " + min + " đến " + max + " mục.");
    }

    private static void text(String value, int max, String path, boolean required) {
        require(!required || (value != null && !value.isBlank()), path + " là bắt buộc.");
        require(value == null || value.length() <= max, path + " vượt quá " + max + " ký tự.");
    }

    private static void url(String value, String path) {
        text(value, 1000, path, false);
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value);
            require(("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null, path + " phải là URL http/https hợp lệ.");
        } catch (IllegalArgumentException ex) {
            throw bad(path + " phải là URL http/https hợp lệ.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw bad(message);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
