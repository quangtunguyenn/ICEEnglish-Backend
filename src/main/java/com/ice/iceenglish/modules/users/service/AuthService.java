package com.ice.iceenglish.modules.users.service;

import com.ice.iceenglish.core.security.JwtService;
import com.ice.iceenglish.modules.users.dto.LoginRequest;
import com.ice.iceenglish.modules.users.dto.StudentCreateRequest;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // Inject JwtService đã tạo

    public User createStudent(StudentCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Tài khoản đã tồn tại!");
        }

        User student = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role("STUDENT")
                .build();

        return userRepository.save(student);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Sai mật khẩu");
        }

        // Sinh JWT Token chứa thông tin username và role của user
        return jwtService.generateToken(user.getUsername(), user.getRole());
    }
}