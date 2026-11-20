package com.ice.iceenglish.modules.videos.controller;

import com.ice.iceenglish.modules.videos.dto.VideoGuestAccessRequest;
import com.ice.iceenglish.modules.videos.dto.VideoPositionRequest;
import com.ice.iceenglish.modules.videos.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/videos")
@RequiredArgsConstructor
public class VideoAdminController {
    private final VideoService service;

    @PutMapping("/{id}/guest-access")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guestAccess(@PathVariable UUID id, @RequestBody VideoGuestAccessRequest request) {
        service.guestAccess(id, request == null ? null : request.guestAccessible());
    }

    @PutMapping("/{videoId}/position")
    @PreAuthorize("hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePosition(@PathVariable UUID videoId, @RequestBody VideoPositionRequest request) {
        service.updatePosition(videoId, request == null ? null : request.position());
    }

    @DeleteMapping("/{videoId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID videoId) {
        service.delete(videoId);
    }
}
