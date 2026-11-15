package com.ice.iceenglish.modules.users.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ice.iceenglish.modules.users.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record StudentResponse(UUID id, String username, String fullName, String studentIrn,
                              String passwordMock,
                              @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy") LocalDate dob,
                              String gender, Boolean isActive, LocalDateTime createdAt) {
    public static StudentResponse from(User user) {
        return new StudentResponse(user.getId(), user.getUsername(), user.getFullName(), user.getStudentIrn(),
                user.getPasswordMock(), user.getDob(), user.getGender(), user.getIsActive(), user.getCreatedAt());
    }
}
