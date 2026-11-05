package com.ice.iceenglish.modules.exams.entity;

import com.ice.iceenglish.modules.exams.enums.PartType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "exam_parts", indexes = @Index(name = "idx_exam_parts_exam", columnList = "exam_id"))
public class ExamPart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_type", nullable = false, length = 50)
    private PartType partType;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(name = "passage_text", columnDefinition = "TEXT")
    private String passageText;

    @Column(name = "translate", columnDefinition = "TEXT")
    private String translate;

    @Column(name = "recommended_time_seconds")
    private Integer recommendedTimeSeconds;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void addQuestion(Question question) {
        questions.add(question);
        question.setPart(this);
    }
}
