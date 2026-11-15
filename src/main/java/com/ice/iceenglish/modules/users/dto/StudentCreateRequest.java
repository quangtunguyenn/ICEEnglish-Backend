package com.ice.iceenglish.modules.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentCreateRequest {
    private String username;
    private String password;
    private String fullName;
    @JsonAlias("student_irn")
    private String studentIrn;
    private String passwordMock;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    private LocalDate dob;
    private String gender;
}