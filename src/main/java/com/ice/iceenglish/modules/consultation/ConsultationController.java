package com.ice.iceenglish.modules.consultation;

import com.ice.iceenglish.modules.consultation.ConsultationDtos.CreateRequest;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.Response;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.StatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consultation-requests")
@RequiredArgsConstructor
public class ConsultationController {
    private final ConsultationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')")
    public List<Response> list(@RequestParam(required = false) ConsultationStatus status,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                               LocalDate date) {
        return service.list(status, date);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN','TEACHER','ROLE_ADMIN','ROLE_TEACHER')")
    public Response updateStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return service.updateStatus(id, request);
    }
}
