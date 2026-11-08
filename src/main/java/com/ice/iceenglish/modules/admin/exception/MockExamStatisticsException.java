package com.ice.iceenglish.modules.admin.exception;

import org.springframework.http.HttpStatus;

public class MockExamStatisticsException extends RuntimeException {

    private final HttpStatus status;

    private MockExamStatisticsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static MockExamStatisticsException notFound(String message) {
        return new MockExamStatisticsException(HttpStatus.NOT_FOUND, message);
    }

    public static MockExamStatisticsException badRequest(String message) {
        return new MockExamStatisticsException(HttpStatus.BAD_REQUEST, message);
    }
}
