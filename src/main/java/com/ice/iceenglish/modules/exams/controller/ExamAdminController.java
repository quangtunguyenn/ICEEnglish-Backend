package com.ice.iceenglish.modules.exams.controller;

import com.ice.iceenglish.modules.exams.dto.ExamGuestAccessRequest;
import com.ice.iceenglish.modules.exams.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exams")
@PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
@RequiredArgsConstructor
public class ExamAdminController {
    private final ExamService service;

    @PutMapping("/{id}/guest-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guestAccess(@PathVariable UUID id, @RequestBody ExamGuestAccessRequest request) {
        service.guestAccess(id, request == null ? null : request.guestAccessible());
    }
}
