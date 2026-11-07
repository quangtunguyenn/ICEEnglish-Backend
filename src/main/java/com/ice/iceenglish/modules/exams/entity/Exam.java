package com.ice.iceenglish.modules.exams.entity;

import com.ice.iceenglish.modules.exams.enums.ExamStatus;
import com.ice.iceenglish.modules.exams.enums.ExamType;
import com.ice.iceenglish.modules.videos.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "exams", indexes = {
        @Index(name = "idx_exams_video_type", columnList = "video_id, exam_type"),
        @Index(name = "idx_exams_status_created", columnList = "status, created_at")
})
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExamStatus status = ExamStatus.DRAFT;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_answers_revealed", nullable = false)
    @Builder.Default
    private boolean answersRevealed = false;

    @Column(name = "shuffle_parts", nullable = false)
    @Builder.Default
    private boolean shuffleParts = false;

    @Column(name = "answers_url", length = 1000)
    private String answersUrl;

    /** Username của người tạo (lấy từ Authentication.getName()). */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", foreignKey = @ForeignKey(name = "fk_exams_video"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Video video;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<ExamPart> parts = new ArrayList<>();

    /** Câu hỏi trực tiếp của HOMEWORK (không qua part). */
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void addQuestion(Question question) {
        questions.add(question);
        question.setExam(this);
    }

    public void addPart(ExamPart part) {
        parts.add(part);
        part.setExam(this);
    }
}
