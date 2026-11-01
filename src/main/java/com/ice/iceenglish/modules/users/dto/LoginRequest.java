package com.ice.iceenglish.modules.users.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}