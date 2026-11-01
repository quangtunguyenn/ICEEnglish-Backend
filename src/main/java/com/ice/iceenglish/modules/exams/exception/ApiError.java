package com.ice.iceenglish.modules.exams.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String error, String message, List<String> details) {

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, details);
    }
}
