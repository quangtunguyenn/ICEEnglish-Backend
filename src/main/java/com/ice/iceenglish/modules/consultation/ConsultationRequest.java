package com.ice.iceenglish.modules.consultation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultation_requests")
@Getter
@Setter
@NoArgsConstructor
public class ConsultationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_fullname", nullable = false, length = 150)
    private String studentFullname;

    @Column(nullable = false)
    private Integer bod;

    @Column(name = "target_grade", nullable = false, columnDefinition = "numeric")
    private BigDecimal targetGrade;

    @Column(name = "phone_number_request", nullable = false, length = 30)
    private String phoneNumberRequest;

    @Column(name = "date_request", nullable = false, updatable = false)
    private LocalDateTime dateRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ConsultationStatus status;
}
