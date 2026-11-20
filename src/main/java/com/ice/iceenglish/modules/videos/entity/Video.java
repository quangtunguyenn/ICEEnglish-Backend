package com.ice.iceenglish.modules.videos.entity;

import com.ice.iceenglish.modules.videos.enums.VideoType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "videos", indexes = {
        @Index(name = "uq_videos_position", columnList = "position", unique = true),
        @Index(name = "idx_videos_position_order",
                columnList = "position_chapter, position_lesson, created_at, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_type", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'LECTURE'")
    @Builder.Default
    private VideoType videoType = VideoType.LECTURE;

    @Column(name = "position", length = 32)
    private String position;

    @Column(name = "position_chapter")
    private Integer positionChapter;

    @Column(name = "position_lesson")
    private Integer positionLesson;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "guest_accessible", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean guestAccessible = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}