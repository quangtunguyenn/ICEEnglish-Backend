package com.ice.iceenglish.modules.admin.exception;

import com.ice.iceenglish.modules.exams.exception.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice(basePackages = "com.ice.iceenglish.modules.admin")
public class MockExamStatisticsExceptionHandler {

    @ExceptionHandler(MockExamStatisticsException.class)
    public ResponseEntity<ApiError> handleStatistics(MockExamStatisticsException exception) {
        return build(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST,
                "Tham số '" + exception.getName() + "' không hợp lệ: '" + exception.getValue() + "'.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, List.of()));
    }
}
