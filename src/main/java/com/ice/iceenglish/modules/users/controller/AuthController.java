package com.ice.iceenglish.modules.users.controller;

import com.ice.iceenglish.modules.users.dto.LoginRequest;
import com.ice.iceenglish.modules.users.dto.StudentCreateRequest;
import com.ice.iceenglish.modules.users.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/students")
    public ResponseEntity<?> createStudent(@RequestBody StudentCreateRequest request) {
        return ResponseEntity.ok(authService.createStudent(request));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}