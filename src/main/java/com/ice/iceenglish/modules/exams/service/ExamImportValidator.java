package com.ice.iceenglish.modules.exams.service;

import com.ice.iceenglish.modules.exams.dto.ExamImportDto;
import com.ice.iceenglish.modules.exams.enums.PartType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm tra logic file JSON import. Lỗi (errors) chặn việc import, cảnh báo (warnings) thì không.
 * Mọi thông báo đều kèm đường dẫn theo file JSON, ví dụ "parts[1].questions[0].correct_option".
 */
@Component
public class ExamImportValidator {

    public record Report(List<String> errors, List<String> warnings) {
    }

    private static final int MAX_NAME_LENGTH = 255;
    private static final List<String> OPTION_KEYS = List.of("A", "B", "C", "D");

    /** Ô trống trong bài cloze, ví dụ "(1) __________". */
    private static final Pattern BLANK = Pattern.compile("\\((\\d{1,4})\\)\\s*_{2,}");

    /** Tiền tố số câu trong nội dung, ví dụ "Question 13:", "Question 23.", "Câu 5)". */
    private static final Pattern QUESTION_PREFIX = Pattern.compile(
            "^\\s*(?:question|câu)\\s*(\\d{1,4})\\s*(?:[.:)]\\s*|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Bỏ tiền tố "Question N" để Frontend tự hiển thị số câu từ order_index, tránh bị lặp. */
    public static String stripQuestionPrefix(String content) {
        if (content == null) {
            return "";
        }
        return QUESTION_PREFIX.matcher(content).replaceFirst("").strip();
    }

    public Report validate(ExamImportDto dto) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (dto == null) {
            errors.add("File JSON rỗng.");
            return new Report(errors, warnings);
        }

        String examTitle = dto.getExamTitle();
        if (isBlank(examTitle) && dto.getExam() != null) {
            examTitle = dto.getExam().getTitle();
        }
        if (isBlank(examTitle)) {
            errors.add("exam_title: bắt buộc.");
        } else if (examTitle.trim().length() > MAX_NAME_LENGTH) {
            errors.add("exam_title: tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }

        Integer duration = dto.getDurationMinutes();
        if (duration == null && dto.getExam() != null) {
            duration = dto.getExam().getDurationMinutes();
        }
        if (duration != null && duration < 1) {
            errors.add("duration_minutes: phải >= 1 (hoặc bỏ trống nếu không giới hạn).");
        }

        List<ExamImportDto.PartImport> parts = dto.getParts();
        if (parts == null || parts.isEmpty()) {
            errors.add("parts: phải có ít nhất 1 phần thi.");
            return new Report(errors, warnings);
        }

        Set<Integer> partOrders = new HashSet<>();
        Set<Integer> questionOrders = new HashSet<>(); // order_index câu hỏi là duy nhất trên toàn đề

        for (int i = 0; i < parts.size(); i++) {
            String path = "parts[" + i + "]";
            ExamImportDto.PartImport part = parts.get(i);
            if (part == null) {
                errors.add(path + ": không được null.");
                continue;
            }
            validatePart(part, path, partOrders, questionOrders, errors, warnings);
        }
        return new Report(errors, warnings);
    }

    private void validatePart(ExamImportDto.PartImport part, String path,
                              Set<Integer> partOrders, Set<Integer> questionOrders,
                              List<String> errors, List<String> warnings) {

        PartType type = PartType.parse(part.getPartType());
        if (isBlank(part.getPartType())) {
            errors.add(path + ".part_type: bắt buộc.");
        } else if (type == null) {
            errors.add(path + ".part_type: giá trị '" + part.getPartType() + "' không hợp lệ. Cho phép: "
                    + Arrays.toString(PartType.values()) + ".");
        }

        if (isBlank(part.getPartName())) {
            errors.add(path + ".part_name: bắt buộc.");
        } else if (part.getPartName().trim().length() > MAX_NAME_LENGTH) {
            errors.add(path + ".part_name: tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }

        Integer order = part.getOrderIndex();
        if (order == null || order < 1) {
            errors.add(path + ".order_index: bắt buộc và phải >= 1.");
        } else if (!partOrders.add(order)) {
            errors.add(path + ".order_index: trùng giá trị " + order + " với một phần thi khác.");
        }

        if (part.getRecommendedTimeSeconds() != null && part.getRecommendedTimeSeconds() < 1) {
            errors.add(path + ".recommended_time_seconds: phải >= 1 (hoặc bỏ trống).");
        } else if (type != null && part.getRecommendedTimeSeconds() != null
                && !part.getRecommendedTimeSeconds().equals(type.getStandardTimeSeconds())) {
            errors.add(path + ".recommended_time_seconds: phải bằng "
                    + type.getStandardTimeSeconds() + " giây theo chuẩn của " + type + ".");
        }

        if (type != null && type.isPassageRequired() && isBlank(part.getPassageText())) {
            errors.add(path + ".passage_text: bắt buộc với part_type " + type + ".");
        }

        List<ExamImportDto.QuestionImport> questions = part.getQuestions();
        if (questions == null || questions.isEmpty()) {
            errors.add(path + ".questions: phải có ít nhất 1 câu hỏi.");
            return;
        }

        Set<Integer> orderIndexesInPart = new HashSet<>();
        for (int j = 0; j < questions.size(); j++) {
            String qPath = path + ".questions[" + j + "]";
            ExamImportDto.QuestionImport q = questions.get(j);
            if (q == null) {
                errors.add(qPath + ": không được null.");
                continue;
            }
            validateQuestion(q, qPath, type, questionOrders, errors, warnings);
            if (q.getOrderIndex() != null) {
                orderIndexesInPart.add(q.getOrderIndex());
            }
        }

        if (type == PartType.CLOZE_TEST && !isBlank(part.getPassageText())) {
            checkClozeBlanks(part.getPassageText(), orderIndexesInPart, path, warnings);
        }
    }

    private void validateQuestion(ExamImportDto.QuestionImport q, String path, PartType type,
                                  Set<Integer> questionOrders,
                                  List<String> errors, List<String> warnings) {

        // content: phần CLOZE_TEST cho phép chỉ là nhãn "Question N" (sau khi bỏ tiền tố sẽ rỗng)
        if (stripQuestionPrefix(q.getContent()).isEmpty() && type != PartType.CLOZE_TEST) {
            errors.add(path + ".content: bắt buộc.");
        }

        Integer order = q.getOrderIndex();
        if (order == null || order < 1) {
            errors.add(path + ".order_index: bắt buộc và phải >= 1.");
        } else if (!questionOrders.add(order)) {
            errors.add(path + ".order_index: trùng giá trị " + order + " với một câu hỏi khác trong đề.");
        }

        if (order != null && q.getContent() != null) {
            Matcher m = QUESTION_PREFIX.matcher(q.getContent());
            if (m.find() && Integer.parseInt(m.group(1)) != order) {
                warnings.add(path + ".content: số câu trong nội dung (" + m.group(1)
                        + ") khác order_index (" + order + ").");
            }
        }

        Map<String, String> options = normalizedOptions(q);
        if (options.isEmpty()) {
            errors.add(path + ".options: bắt buộc (đủ A, B, C, D).");
        } else {
            for (String key : OPTION_KEYS) {
                if (isBlank(options.get(key))) {
                    errors.add(path + ".options." + key + ": bắt buộc.");
                }
            }
            for (String key : options.keySet()) {
                if (!OPTION_KEYS.contains(key)) {
                    errors.add(path + ".options." + key + ": khóa không hợp lệ (chỉ cho phép A, B, C, D).");
                }
            }
        }

        if (isBlank(q.getCorrectOption())) {
            errors.add(path + ".correct_option: bắt buộc.");
        } else if (!OPTION_KEYS.contains(q.getCorrectOption().trim())) {
            errors.add(path + ".correct_option: '" + q.getCorrectOption() + "' không hợp lệ (A, B, C hoặc D).");
        }
    }

    private Map<String, String> normalizedOptions(ExamImportDto.QuestionImport q) {
        if (q.getOptions() != null) {
            return q.getOptions();
        }
        Map<String, String> options = new java.util.LinkedHashMap<>();
        options.put("A", q.getOptA());
        options.put("B", q.getOptB());
        options.put("C", q.getOptC());
        options.put("D", q.getOptD());
        return options;
    }

    /** Số trong "(n) ____" của passage phải khớp order_index của các câu hỏi trong phần cloze. */
    private void checkClozeBlanks(String passage, Set<Integer> questionOrders, String path, List<String> warnings) {
        Set<Integer> blanks = new TreeSet<>();
        Matcher m = BLANK.matcher(passage);
        while (m.find()) {
            blanks.add(Integer.parseInt(m.group(1)));
        }

        if (blanks.isEmpty()) {
            warnings.add(path + ".passage_text: không tìm thấy ô trống dạng \"(1) ______\".");
            return;
        }

        Set<Integer> missingQuestions = new TreeSet<>(blanks);
        missingQuestions.removeAll(questionOrders);
        Set<Integer> orphanQuestions = new TreeSet<>(questionOrders);
        orphanQuestions.removeAll(blanks);

        if (!missingQuestions.isEmpty()) {
            warnings.add(path + ": ô trống " + missingQuestions + " trong passage_text chưa có câu hỏi tương ứng.");
        }
        if (!orphanQuestions.isEmpty()) {
            warnings.add(path + ": câu hỏi " + orphanQuestions + " không có ô trống tương ứng trong passage_text.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
