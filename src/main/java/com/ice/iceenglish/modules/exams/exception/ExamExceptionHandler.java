package com.ice.iceenglish.modules.exams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Chỉ áp dụng cho controller của module exams.
 * Cố ý KHÔNG có handler bắt Exception chung: AccessDeniedException phải đi tiếp tới Spring Security để trả 403.
 */
@RestControllerAdvice(basePackages = "com.ice.iceenglish.modules.exams")
public class ExamExceptionHandler {

    @ExceptionHandler(ExamImportException.class)
    public ResponseEntity<ApiError> handleImport(ExamImportException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), e.getErrors());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Tham số '" + e.getName() + "' không hợp lệ: '" + e.getValue() + "'.";
        return build(HttpStatus.BAD_REQUEST, message, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, details));
    }
}
