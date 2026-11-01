package com.ice.iceenglish.modules.submissions.entity;

import com.ice.iceenglish.modules.exams.entity.ExamPart;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "part_time_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_part_time_logs_submission_part",
                columnNames = {"submission_id", "part_id"}))
public class PartTimeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private ExamPart part;

    @Column(name = "time_spent_seconds", nullable = false)
    private Integer timeSpentSeconds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}