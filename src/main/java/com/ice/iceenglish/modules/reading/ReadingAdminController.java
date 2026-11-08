package com.ice.iceenglish.modules.reading;

import com.ice.iceenglish.modules.reading.ReadingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reading-blogs")
@PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
@RequiredArgsConstructor
public class ReadingAdminController {
    private final ReadingService service;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importFile(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return service.importFile(file, authentication.getName());
    }

    @GetMapping
    public Page list(@RequestParam(required = false) String topic,
            @RequestParam(required = false) String contentType, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return service.list(true, false, topic, contentType, keyword, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminDetail detail(@PathVariable UUID id) {
        return service.adminDetail(id);
    }

    @PatchMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@PathVariable UUID id) { service.publish(id); }

    @PatchMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) { service.archive(id); }

    @PutMapping("/{id}/guest-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guestAccess(@PathVariable UUID id, @RequestBody GuestAccessRequest request) {
        service.guestAccess(id, request);
    }
}
