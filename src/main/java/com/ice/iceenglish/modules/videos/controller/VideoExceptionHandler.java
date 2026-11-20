package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.modules.exams.exception.ApiError;
import com.ice.iceenglish.modules.videos.exception.VideoPositionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(basePackageClasses = VideoAdminController.class)
public class VideoExceptionHandler {
    @ExceptionHandler(VideoPositionException.class)
    public ResponseEntity<ApiError> status(VideoPositionException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiError.of(status, exception.getReason(), List.of()));
    }
}
