package com.ice.iceenglish.modules.exams.entity;

import com.ice.iceenglish.modules.videos.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_questions_part", columnList = "part_id"),
        @Index(name = "idx_questions_exam", columnList = "exam_id")
})
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null với câu hỏi của HOMEWORK (homework không có part, câu hỏi nối thẳng vào exam). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private ExamPart part;

    /** Chỉ dùng cho HOMEWORK; câu hỏi của đề thường thuộc exam thông qua part. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    /** Nội dung câu hỏi, đã bỏ tiền tố "Question N". Có thể rỗng với phần CLOZE_TEST. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "opt_a", nullable = false, columnDefinition = "TEXT")
    private String optA;

    @Column(name = "opt_b", nullable = false, columnDefinition = "TEXT")
    private String optB;

    @Column(name = "opt_c", nullable = false, columnDefinition = "TEXT")
    private String optC;

    @Column(name = "opt_d", nullable = false, columnDefinition = "TEXT")
    private String optD;

    /** Cột DB là CHAR(1); @JdbcTypeCode giúp Hibernate (ddl-auto: validate) khớp kiểu bpchar. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "correct_opt", nullable = false, length = 1)
    private String correctOpt;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_video_id")
    private Video relatedVideo;

    /** Số thứ tự câu trên toàn đề (1, 2, 13, 14, ...). */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
