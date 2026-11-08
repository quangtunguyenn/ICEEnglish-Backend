package com.ice.iceenglish.modules.reading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadingService {
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> TYPES = Set.of("ARTICLE", "MAGAZINE", "STORY", "PASSAGE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReadingImportValidator validator;

    @Transactional
    public ImportResult importFile(MultipartFile file, String username) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "File JSON phải có kích thước từ 1 byte đến 2 MiB.");
        }
        ImportFile input;
        try (var stream = file.getInputStream()) {
            input = mapper.readValue(stream, ImportFile.class);
        } catch (JsonProcessingException e) {
            throw error(HttpStatus.BAD_REQUEST, "JSON không hợp lệ: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được file JSON.", e);
        }
        validator.validate(input);
        BlogInput b = input.blog();
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO reading_topics(id,slug,name) VALUES (?,?,?) ON CONFLICT (slug) DO NOTHING",
                topicId, b.topic().slug(), b.topic().name().trim());
        var topics = jdbc.query("SELECT id,name FROM reading_topics WHERE slug=?",
                (rs, row) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("name")}, b.topic().slug());
        Object[] topic = topics.get(0);
        if (!topic[1].equals(b.topic().name().trim())) {
            throw error(HttpStatus.CONFLICT, "Tên chủ đề không khớp với slug đã tồn tại.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reading_blogs(id,topic_id,title,summary,content_type,difficulty,
                  estimated_reading_minutes,passage_blocks,cover_image_url,source_name,source_url,
                  guest_accessible,created_by)
                VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?)
                """, id, topic[0], b.title().trim(), b.summary().trim(), b.contentType(), b.difficulty(),
                b.estimatedReadingMinutes(), json(input.passageBlocks()), b.coverImageUrl(), b.sourceName(),
                b.sourceUrl(), b.guestAccessible(), username);
        for (VocabularyInput v : input.vocabularies()) {
            jdbc.update("""
                    INSERT INTO reading_vocabularies(id,blog_id,order_index,word,phonetic,meaning,
                      example_sentence,example_translation,passage_block_id) VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), id, v.orderIndex(), v.word().trim(), v.phonetic(),
                    v.meaning(), v.exampleSentence(), v.exampleTranslation(), v.passageBlockId());
        }
        for (QuestionInput q : input.questions()) {
            UUID qid = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO reading_questions(id,blog_id,order_index,content,skill_type,evidence_block_ids,explanation)
                    VALUES (?,?,?,?,?,CAST(? AS jsonb),?)
                    """, qid, id, q.orderIndex(), q.content(), q.skillType(), json(q.evidenceBlockIds()), q.explanation());
            for (AnswerInput answer : q.answers()) {
                jdbc.update("""
                        INSERT INTO reading_answers(id,question_id,option_key,content,is_correct,explanation)
                        VALUES (?,?,?,?,?,?)
                        """, UUID.randomUUID(), qid, answer.optionKey(), answer.content(),
                        answer.isCorrect(), answer.explanation());
            }
        }
        return new ImportResult(id, "DRAFT", 1);
    }

    @Transactional(readOnly = true)
    public Page list(boolean admin, boolean guest, String topic, String type, String keyword,
                     String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw error(HttpStatus.BAD_REQUEST, "page phải >= 0 và size phải từ 1 đến 100.");
        }
        if (status != null && (!admin || !STATUS.contains(status))) {
            throw error(HttpStatus.BAD_REQUEST, "status không hợp lệ.");
        }
        if (type != null && !TYPES.contains(type)) {
            throw error(HttpStatus.BAD_REQUEST, "contentType không hợp lệ.");
        }
        if (keyword != null && keyword.length() > 200) {
            throw error(HttpStatus.BAD_REQUEST, "keyword tối đa 200 ký tự.");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!admin) where.append(" AND b.status='PUBLISHED'");
        else if (status != null) { where.append(" AND b.status=?"); args.add(status); }
        if (topic != null && !topic.isBlank()) { where.append(" AND t.slug=?"); args.add(topic); }
        if (type != null) { where.append(" AND b.content_type=?"); args.add(type); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND strpos(lower(b.title), lower(?)) > 0");
            args.add(keyword.trim());
        }
        String from = " FROM reading_blogs b JOIN reading_topics t ON t.id=b.topic_id";
        long count = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, args.toArray());
        List<Object> params = new ArrayList<>(args);
        params.add(size);
        params.add(page * size);
        List<ListItem> items = jdbc.query("SELECT b.*,t.slug,t.name" + from + where
                        + " ORDER BY b.published_at DESC NULLS LAST,b.created_at DESC,b.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> item(rs, admin, guest), params.toArray());
        return new Page(items, page, size, count, (count + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public List<Topic> topics() {
        return jdbc.query("""
                SELECT DISTINCT t.slug,t.name FROM reading_topics t
                JOIN reading_blogs b ON b.topic_id=t.id WHERE b.status='PUBLISHED' ORDER BY t.name,t.slug
                """, (rs, row) -> new Topic(rs.getString("slug"), rs.getString("name")));
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, boolean guest) {
        Row blog = getBlog(id, false, guest);
        return new Detail(blog.item(), blog.revision(), readBlocks(blog.blocks()), questions(id),
                vocabulary(id), blog.sourceName(), blog.sourceUrl());
    }

    @Transactional(readOnly = true)
    public AdminDetail adminDetail(UUID id) {
        Row blog = getBlog(id, true, false);
        return new AdminDetail(blog.item(), blog.revision(), readBlocks(blog.blocks()),
                adminQuestions(id), vocabulary(id), blog.sourceName(), blog.sourceUrl(), blog.createdBy());
    }

    @Transactional(readOnly = true)
    public List<Vocabulary> vocabularies(UUID id, boolean guest) {
        getBlog(id, false, guest);
        return vocabulary(id);
    }

    @Transactional(readOnly = true)
    public AnswerResult answer(UUID id, UUID questionId, AnswerRequest request, boolean guest) {
        Row blog = getBlog(id, false, guest);
        if (request == null || request.revision() == null || request.selectedOption() == null
                || !Set.of("A", "B", "C", "D").contains(request.selectedOption())) {
            throw error(HttpStatus.BAD_REQUEST, "revision và selectedOption A, B, C hoặc D là bắt buộc.");
        }
        if (blog.revision() != request.revision()) {
            throw error(HttpStatus.CONFLICT, "Bài đọc đã thay đổi, vui lòng tải lại.");
        }
        List<AnswerResult> results = jdbc.query("""
                SELECT q.id,q.explanation,q.evidence_block_ids,a.option_key,a.is_correct,a.explanation AS option_explanation
                FROM reading_questions q JOIN reading_answers a ON a.question_id=q.id
                WHERE q.blog_id=? AND q.id=? AND a.option_key=?
                """, (rs, n) -> new AnswerResult(rs.getObject("id", UUID.class), rs.getString("option_key"),
                rs.getBoolean("is_correct"), rs.getString("option_explanation"), rs.getString("explanation"),
                readStrings(rs.getString("evidence_block_ids"))), id, questionId, request.selectedOption());
        if (results.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi hoặc lựa chọn.");
        return results.get(0);
    }

    @Transactional
    public void publish(UUID id) {
        Row blog = getBlog(id, true, false);
        Integer invalid = jdbc.queryForObject("""
                SELECT count(*) FROM reading_questions q WHERE q.blog_id=? AND
                  ((SELECT count(*) FROM reading_answers a WHERE a.question_id=q.id)<>4 OR
                   (SELECT count(*) FROM reading_answers a WHERE a.question_id=q.id AND a.is_correct)<>1)
                """, Integer.class, id);
        Integer total = jdbc.queryForObject("SELECT count(*) FROM reading_questions WHERE blog_id=?", Integer.class, id);
        if (total == 0 || invalid != 0 || readBlocks(blog.blocks()).isEmpty()) {
            throw error(HttpStatus.CONFLICT, "Bài đọc chưa đủ nội dung hợp lệ để xuất bản.");
        }
        jdbc.update("""
                UPDATE reading_blogs SET status='PUBLISHED',published_at=COALESCE(published_at,now()),
                  updated_at=now() WHERE id=?
                """, id);
    }

    @Transactional
    public void archive(UUID id) {
        updateExisting("UPDATE reading_blogs SET status='ARCHIVED',updated_at=now() WHERE id=?", id);
    }

    @Transactional
    public void guestAccess(UUID id, GuestAccessRequest request) {
        if (request == null || request.guestAccessible() == null) {
            throw error(HttpStatus.BAD_REQUEST, "guestAccessible là bắt buộc.");
        }
        int changed = jdbc.update("UPDATE reading_blogs SET guest_accessible=?,updated_at=now() WHERE id=?",
                request.guestAccessible(), id);
        if (changed == 0) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy bài đọc.");
    }

    private void updateExisting(String sql, UUID id) {
        if (jdbc.update(sql, id) == 0) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy bài đọc.");
    }

    private Row getBlog(UUID id, boolean admin, boolean guest) {
        String access = admin ? "" : guest
                ? " AND b.status='PUBLISHED' AND b.guest_accessible=true" : " AND b.status='PUBLISHED'";
        List<Row> rows = jdbc.query("""
                SELECT b.*,t.slug,t.name FROM reading_blogs b
                JOIN reading_topics t ON t.id=b.topic_id WHERE b.id=?
                """ + access, (rs, n) -> new Row(item(rs, admin, guest), rs.getInt("revision"),
                rs.getString("passage_blocks"), rs.getString("source_name"), rs.getString("source_url"),
                rs.getString("created_by")), id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy bài đọc.");
        return rows.get(0);
    }

    private static ListItem item(ResultSet rs, boolean admin, boolean guest) throws SQLException {
        boolean free = rs.getBoolean("guest_accessible");
        return new ListItem(rs.getObject("id", UUID.class), rs.getString("title"),
                new Topic(rs.getString("slug"), rs.getString("name")), rs.getString("summary"),
                rs.getString("content_type"), rs.getString("difficulty"),
                rs.getObject("estimated_reading_minutes", Integer.class), rs.getString("cover_image_url"),
                !guest || free, rs.getString("status"), free, rs.getObject("published_at", OffsetDateTime.class));
    }

    private List<Question> questions(UUID id) {
        Map<UUID, Question> questions = new LinkedHashMap<>();
        jdbc.query("""
                SELECT q.id AS qid,q.order_index,q.content AS question_content,q.skill_type,
                       a.id AS aid,a.option_key,a.content AS answer_content
                FROM reading_questions q JOIN reading_answers a ON a.question_id=q.id
                WHERE q.blog_id=? ORDER BY q.order_index,a.option_key
                """, rs -> {
            UUID qid = rs.getObject("qid", UUID.class);
            Question q = questions.get(qid);
            if (q == null) {
                q = new Question(qid, rs.getInt("order_index"), rs.getString("question_content"),
                        rs.getString("skill_type"), new ArrayList<>());
                questions.put(qid, q);
            }
            q.answers().add(new Answer(rs.getObject("aid", UUID.class),
                    rs.getString("option_key"), rs.getString("answer_content")));
        }, id);
        return List.copyOf(questions.values());
    }

    private List<AdminQuestion> adminQuestions(UUID id) {
        Map<UUID, AdminQuestion> questions = new LinkedHashMap<>();
        jdbc.query("""
                SELECT q.id AS qid,q.order_index,q.content AS question_content,q.skill_type,
                       q.evidence_block_ids,q.explanation AS question_explanation,
                       a.id AS aid,a.option_key,a.content AS answer_content,a.is_correct,a.explanation AS answer_explanation
                FROM reading_questions q JOIN reading_answers a ON a.question_id=q.id
                WHERE q.blog_id=? ORDER BY q.order_index,a.option_key
                """, rs -> {
            UUID qid = rs.getObject("qid", UUID.class);
            AdminQuestion q = questions.get(qid);
            if (q == null) {
                q = new AdminQuestion(qid, rs.getInt("order_index"), rs.getString("question_content"),
                        rs.getString("skill_type"), readStrings(rs.getString("evidence_block_ids")),
                        rs.getString("question_explanation"), new ArrayList<>());
                questions.put(qid, q);
            }
            q.answers().add(new AdminAnswer(rs.getObject("aid", UUID.class), rs.getString("option_key"),
                    rs.getString("answer_content"), rs.getBoolean("is_correct"), rs.getString("answer_explanation")));
        }, id);
        return List.copyOf(questions.values());
    }

    private List<Vocabulary> vocabulary(UUID id) {
        return jdbc.query("SELECT * FROM reading_vocabularies WHERE blog_id=? ORDER BY order_index",
                (rs, n) -> new Vocabulary(rs.getObject("id", UUID.class), rs.getInt("order_index"),
                        rs.getString("word"), rs.getString("phonetic"), rs.getString("meaning"),
                        rs.getString("example_sentence"), rs.getString("example_translation"),
                        rs.getString("passage_block_id")), id);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Không thể lưu JSON bài đọc.", e); }
    }
    private List<Block> readBlocks(String value) {
        try { return mapper.readValue(value, new TypeReference<List<Block>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Dữ liệu đoạn văn không hợp lệ.", e); }
    }
    private List<String> readStrings(String value) {
        try { return mapper.readValue(value, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Dữ liệu bằng chứng không hợp lệ.", e); }
    }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
    private record Row(ListItem item, int revision, String blocks, String sourceName, String sourceUrl, String createdBy) {}
}
