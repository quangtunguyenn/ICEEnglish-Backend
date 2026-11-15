package com.ice.iceenglish.modules.consultation;

import com.ice.iceenglish.modules.consultation.ConsultationDtos.CreateRequest;
import com.ice.iceenglish.modules.consultation.ConsultationDtos.StatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsultationServiceTest {
    private final ConsultationRequestRepository repository = mock(ConsultationRequestRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T17:30:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ConsultationService service = new ConsultationService(repository, clock);

    @Test
    void createsWithServerTimeAndDefaultStatus() {
        when(repository.save(any())).thenAnswer(call -> {
            ConsultationRequest request = call.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });
        var result = service.create(new CreateRequest(" Nguyen Van A ", BigDecimal.valueOf(2008),
                new BigDecimal("8.5"), " 0901234567 "));
        assertNotNull(result.id());
        assertEquals("Nguyen Van A", result.studentFullname());
        assertEquals("0901234567", result.phoneNumberRequest());
        assertEquals(new BigDecimal("8.5"), result.targetGrade());
        assertEquals(LocalDateTime.of(2026, 1, 2, 0, 30), result.dateRequest());
        assertEquals(ConsultationStatus.NOT_DONE, result.status());
    }

    @Test
    void rejectsInvalidInputsBeforeSaving() {
        List<CreateRequest> invalid = List.of(
                new CreateRequest(" ", BigDecimal.valueOf(2008), BigDecimal.TEN, "090"),
                new CreateRequest("A", BigDecimal.valueOf(2027), BigDecimal.TEN, "090"),
                new CreateRequest("A", BigDecimal.ZERO, BigDecimal.TEN, "090"),
                new CreateRequest("A", BigDecimal.valueOf(2008), new BigDecimal("-0.1"), "090"),
                new CreateRequest("A", BigDecimal.valueOf(2008), new BigDecimal("10.1"), "090"),
                new CreateRequest("A", BigDecimal.valueOf(2008), null, "090"),
                new CreateRequest("A", BigDecimal.valueOf(2008), BigDecimal.ONE, " "),
                new CreateRequest("A".repeat(151), BigDecimal.valueOf(2008), BigDecimal.ONE, "090"));
        for (CreateRequest input : invalid) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(input))
                    .getStatusCode().value());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void selectsAllFilterCombinationsUsingHalfOpenDay() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        service.list(null, null);
        service.list(ConsultationStatus.DONE, null);
        service.list(null, date);
        service.list(ConsultationStatus.NOT_DONE, date);
        verify(repository).findAllByOrderByDateRequestDescIdDesc();
        verify(repository).findByStatusOrderByDateRequestDescIdDesc(ConsultationStatus.DONE);
        verify(repository).findByDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(start, end);
        verify(repository).findByStatusAndDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(
                ConsultationStatus.NOT_DONE, start, end);
    }

    @Test
    void updatesStatusOrReportsMissingAndInvalid() {
        UUID id = UUID.randomUUID();
        ConsultationRequest request = new ConsultationRequest();
        request.setId(id);
        request.setStatus(ConsultationStatus.NOT_DONE);
        when(repository.findById(id)).thenReturn(Optional.of(request));
        assertEquals(ConsultationStatus.DONE, service.updateStatus(id, new StatusRequest(ConsultationStatus.DONE)).status());
        assertEquals(ConsultationStatus.NOT_DONE,
                service.updateStatus(id, new StatusRequest(ConsultationStatus.NOT_DONE)).status());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(id, new StatusRequest(null))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(UUID.randomUUID(), new StatusRequest(ConsultationStatus.DONE)))
                .getStatusCode().value());
    }
}
