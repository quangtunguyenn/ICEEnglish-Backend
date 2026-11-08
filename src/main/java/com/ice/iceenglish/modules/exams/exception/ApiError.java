package com.ice.iceenglish.modules.exams.exception;

import com.ice.iceenglish.core.time.ServerTime;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        OffsetDateTime timestamp, int status, String error, String message, List<String> details) {

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(
                OffsetDateTime.now(ServerTime.CLOCK),
                status.value(),
                status.getReasonPhrase(),
                message,
                details);
    }
}
