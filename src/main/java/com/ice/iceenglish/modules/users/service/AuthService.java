package com.ice.iceenglish.modules.users.service;

import com.ice.iceenglish.core.security.JwtService;
import com.ice.iceenglish.modules.users.dto.LoginRequest;
import com.ice.iceenglish.modules.users.dto.StudentCreateRequest;
import com.ice.iceenglish.modules.users.dto.UserProfileResponse;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // Inject JwtService đã tạo
    private final LoginProtection loginProtection;

    public User createStudent(StudentCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Tài khoản đã tồn tại!");
        }

        User student = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .studentIrn(request.getStudentIrn())
                .passwordMock(request.getPasswordMock())
                .dob(request.getDob())
                .gender(request.getGender())
                .role("STUDENT")
                .build();

        return userRepository.save(student);
    }

    public String login(LoginRequest request) {
        loginProtection.assertNotLocked(request.getUsername());
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    loginProtection.recordFailure(request.getUsername());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác.");
                });

        if (!loginProtection.verify(() -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))) {
            loginProtection.recordFailure(request.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác.");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản đã bị vô hiệu hóa.");
        }
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase(Locale.ROOT);
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalStateException("Role người dùng không hợp lệ.");
        }
        String token = jwtService.generateToken(user.getUsername(), role);
        loginProtection.clearFailures(request.getUsername());
        return token;
    }

    public UserProfileResponse getProfile(String username, List<String> authorities) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đăng nhập."));
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .studentIrn(user.getStudentIrn())
                .passwordMock(user.getPasswordMock())
                .dob(user.getDob())
                .gender(user.getGender())
                .isActive(user.getIsActive())
                .role(user.getRole())
                .authorities(authorities)
                .build();
    }
}