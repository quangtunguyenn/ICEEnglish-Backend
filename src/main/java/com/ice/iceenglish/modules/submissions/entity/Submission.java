package com.ice.iceenglish.modules.submissions.entity;


import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.exams.entity.Exam;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "submissions", indexes = {
        @Index(name = "idx_submissions_user_exam_mode", columnList = "user_id, exam_id, mode, started_at"),
        @Index(name = "idx_submissions_user_mode_submitted", columnList = "user_id, mode, submitted_at")
})
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false, length = 20)
    private String mode; // 'GENERAL_EXAM', 'MOCK', 'HOMEWORK'

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "total_correct")
    @Builder.Default
    private Integer totalCorrect = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "part_order", columnDefinition = "jsonb")
    private List<UUID> partOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}