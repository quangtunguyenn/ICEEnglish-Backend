package com.ice.iceenglish.modules.reading;

import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/reading-blogs", "/api/v1/guest/reading-blogs"})
@RequiredArgsConstructor
public class ReadingController {
    private final ReadingService service;

    @GetMapping
    public Page list(HttpServletRequest request, @RequestParam(required = false) String topic,
            @RequestParam(required = false) String contentType, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return service.list(false, guest(request), topic, contentType, keyword, null, page, size);
    }

    @GetMapping("/topics")
    public List<Topic> topics() {
        return service.topics();
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id, HttpServletRequest request) {
        return service.detail(id, guest(request));
    }

    @GetMapping("/{id}/vocabularies")
    public List<Vocabulary> vocabularies(@PathVariable UUID id, HttpServletRequest request) {
        return service.vocabularies(id, guest(request));
    }

    @PostMapping("/{id}/questions/{questionId}/answer")
    public AnswerResult answer(@PathVariable UUID id, @PathVariable UUID questionId,
            @RequestBody AnswerRequest answer, HttpServletRequest request) {
        return service.answer(id, questionId, answer, guest(request));
    }

    private boolean guest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/v1/guest/");
    }
}
