package com.ice.iceenglish.modules.users.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String role;
    private List<String> authorities;
}
