package com.ice.iceenglish.modules.consultation;

import com.ice.iceenglish.modules.consultation.ConsultationDtos.CreateRequest;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.Response;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.StatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultationService {
    private final ConsultationRequestRepository repository;
    private final Clock serverClock;

    @Transactional
    public Response create(CreateRequest input) {
        if (input == null || input.studentFullname() == null || input.studentFullname().isBlank()
                || input.studentFullname().trim().length() > 150) {
            throw badRequest("studentFullname is required and must be at most 150 characters");
        }
        if (input.bod() == null || input.bod().stripTrailingZeros().scale() > 0
                || input.bod().compareTo(BigDecimal.ONE) < 0
                || input.bod().compareTo(BigDecimal.valueOf(LocalDate.now(serverClock).getYear())) > 0) {
            throw badRequest("bod must be a birth year between 1 and the current year");
        }
        if (input.targetGrade() == null || input.targetGrade().compareTo(BigDecimal.ZERO) < 0
                || input.targetGrade().compareTo(BigDecimal.TEN) > 0) {
            throw badRequest("targetGrade must be between 0 and 10");
        }
        if (input.phoneNumberRequest() == null || input.phoneNumberRequest().isBlank()
                || input.phoneNumberRequest().trim().length() > 30) {
            throw badRequest("phoneNumberRequest is required and must be at most 30 characters");
        }

        ConsultationRequest request = new ConsultationRequest();
        request.setStudentFullname(input.studentFullname().trim());
        request.setBod(input.bod().intValueExact());
        request.setTargetGrade(input.targetGrade());
        request.setPhoneNumberRequest(input.phoneNumberRequest().trim());
        request.setDateRequest(LocalDateTime.now(serverClock));
        request.setStatus(ConsultationStatus.NOT_DONE);
        return Response.from(repository.save(request));
    }

    @Transactional(readOnly = true)
    public List<Response> list(ConsultationStatus status, LocalDate date) {
        List<ConsultationRequest> requests;
        if (date == null) {
            requests = status == null ? repository.findAllByOrderByDateRequestDescIdDesc()
                    : repository.findByStatusOrderByDateRequestDescIdDesc(status);
        } else {
            if (date.getYear() < 1 || date.getYear() > 9999) {
                throw badRequest("date must have a year between 0001 and 9999");
            }
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            requests = status == null
                    ? repository.findByDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(start, end)
                    : repository.findByStatusAndDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(
                            status, start, end);
        }
        return requests.stream().map(Response::from).toList();
    }

    @Transactional
    public Response updateStatus(UUID id, StatusRequest input) {
        if (input == null || input.status() == null) {
            throw badRequest("status is required");
        }
        ConsultationRequest request = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation request not found"));
        request.setStatus(input.status());
        return Response.from(request);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
