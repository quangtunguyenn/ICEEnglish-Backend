package com.ice.iceenglish.modules.users.controller;

import com.ice.iceenglish.modules.users.dto.StudentResponse;
import com.ice.iceenglish.modules.users.service.StudentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/students")
@PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
@RequiredArgsConstructor
public class StudentManagementController {
    private final StudentManagementService service;

    public record ActiveRequest(Boolean isActive) {}

    @GetMapping
    public List<StudentResponse> list(@RequestParam(required = false) Boolean isActive,
                                      @RequestParam(required = false) String keyword) {
        return service.list(isActive, keyword);
    }

    @PatchMapping("/{id}/is-active")
    public StudentResponse setActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return service.setActive(id, request.isActive());
    }
}
