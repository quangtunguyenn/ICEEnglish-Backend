package com.ice.iceenglish.modules.users.dto;

import lombok.Data;

@Data
public class StudentCreateRequest {
    private String username;
    private String password;
    private String fullName;
}