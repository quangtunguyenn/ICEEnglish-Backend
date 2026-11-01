package com.ice.iceenglish.modules.exams.exception;

import java.util.List;

/** Lỗi dữ liệu khi import đề. Mang theo danh sách lỗi chi tiết theo vị trí, ví dụ "parts[1].questions[0].correct_option: ...". */
public class ExamImportException extends RuntimeException {

    private final List<String> errors;

    public ExamImportException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ExamImportException(List<String> errors) {
        super("Dữ liệu đề thi không hợp lệ (" + errors.size() + " lỗi).");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
