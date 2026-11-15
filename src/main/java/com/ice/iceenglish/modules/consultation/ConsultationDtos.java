package com.ice.iceenglish.modules.consultation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class ConsultationDtos {
    private ConsultationDtos() {}

    public record CreateRequest(String studentFullname, BigDecimal bod, BigDecimal targetGrade,
                                String phoneNumberRequest) {}

    public record StatusRequest(ConsultationStatus status) {}

    public record Response(UUID id, String studentFullname, Integer bod, BigDecimal targetGrade,
                           String phoneNumberRequest, LocalDateTime dateRequest, ConsultationStatus status) {
        static Response from(ConsultationRequest request) {
            return new Response(request.getId(), request.getStudentFullname(), request.getBod(),
                    request.getTargetGrade(), request.getPhoneNumberRequest(), request.getDateRequest(),
                    request.getStatus());
        }
    }
}
