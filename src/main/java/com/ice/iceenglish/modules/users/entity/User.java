package com.ice.iceenglish.modules.users.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    @JsonIgnore
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role; // 'STUDENT', 'TEACHER', 'ADMIN'

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "student_irn", length = 50)
    private String studentIrn;

    @Column(name = "password_mock", length = 100)
    private String passwordMock;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(length = 20)
    private String gender;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}