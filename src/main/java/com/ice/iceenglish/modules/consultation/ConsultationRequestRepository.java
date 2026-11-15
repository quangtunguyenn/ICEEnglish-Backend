package com.ice.iceenglish.modules.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, UUID> {
    List<ConsultationRequest> findAllByOrderByDateRequestDescIdDesc();

    List<ConsultationRequest> findByStatusOrderByDateRequestDescIdDesc(ConsultationStatus status);

    List<ConsultationRequest> findByDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(
            LocalDateTime start, LocalDateTime end);

    List<ConsultationRequest> findByStatusAndDateRequestGreaterThanEqualAndDateRequestLessThanOrderByDateRequestDescIdDesc(
            ConsultationStatus status, LocalDateTime start, LocalDateTime end);
}
