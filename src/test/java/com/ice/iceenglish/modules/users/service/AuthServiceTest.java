package com.ice.iceenglish.modules.users.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ice.iceenglish.core.security.JwtService;
import com.ice.iceenglish.modules.users.dto.StudentCreateRequest;
import com.ice.iceenglish.modules.users.dto.LoginRequest;
import com.ice.iceenglish.modules.users.dto.UserProfileResponse;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final LoginProtection loginProtection = mock(LoginProtection.class);
    private final AuthService service =
            new AuthService(userRepository, passwordEncoder, jwtService, loginProtection);

    @Test
    void createsStudentWithOptionalProfileFields() {
        StudentCreateRequest request = new StudentCreateRequest();
        request.setUsername("student001");
        request.setPassword("temporary-password");
        request.setFullName("Nguyen Van A");
        request.setStudentIrn("IRN-001");
        request.setPasswordMock("practice-123");
        request.setDob(LocalDate.of(2010, 5, 6));
        request.setGender("FEMALE");
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.createStudent(request);

        assertEquals("IRN-001", created.getStudentIrn());
        assertEquals("practice-123", created.getPasswordMock());
        assertEquals(LocalDate.of(2010, 5, 6), created.getDob());
        assertEquals("FEMALE", created.getGender());
        assertEquals("encoded", created.getPasswordHash());
        assertTrue(created.getIsActive());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void inactiveUserWithCorrectPasswordCannotLoginOrClearFailures() {
        LoginRequest request = new LoginRequest();
        request.setUsername("student");
        request.setPassword("secret");
        User user = User.builder().username("student").role("STUDENT").isActive(false).build();
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(loginProtection.verify(any(BooleanSupplier.class))).thenReturn(true);

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.login(request));
        verify(loginProtection, never()).clearFailures(anyString());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    void missingUserAndIncorrectPasswordReturnSameUnauthorizedResponse() {
        LoginRequest request = new LoginRequest();
        request.setUsername("student");
        request.setPassword("wrong");
        when(userRepository.findByUsername("student")).thenReturn(Optional.empty());
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> service.login(request));

        when(userRepository.findByUsername("student")).thenReturn(Optional.of(
                User.builder().username("student").role("STUDENT").build()));
        when(loginProtection.verify(any(BooleanSupplier.class))).thenReturn(false);
        ResponseStatusException wrong = assertThrows(ResponseStatusException.class, () -> service.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
        assertEquals(missing.getStatusCode(), wrong.getStatusCode());
        assertEquals(missing.getReason(), wrong.getReason());
        verify(loginProtection, org.mockito.Mockito.times(2)).recordFailure("student");
    }

    @Test
    void serializedUserDoesNotExposePasswordHash() throws Exception {
        User user = User.builder().username("student").passwordHash("encoded").role("STUDENT").build();
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(user));
        assertEquals("student", json.get("username").asText());
        assertNull(json.get("passwordHash"));
    }

    @Test
    void loginClearsFailuresOnlyAfterEveryConditionPasses() {
        LoginRequest request = new LoginRequest();
        request.setUsername("student");
        request.setPassword("secret");
        User user = User.builder().username("student").role("STUDENT").build();
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(loginProtection.verify(any(BooleanSupplier.class))).thenReturn(true);
        when(jwtService.generateToken("student", "STUDENT")).thenReturn("jwt");
        assertEquals("jwt", service.login(request));
        verify(loginProtection).clearFailures("student");
        user.setRole("invalid");
        assertThrows(IllegalStateException.class, () -> service.login(request));
        verify(loginProtection, org.mockito.Mockito.times(1)).clearFailures("student");
    }

    @Test
    void tokenGenerationFailureDoesNotClearFailures() {
        LoginRequest request = new LoginRequest();
        request.setUsername("student");
        request.setPassword("secret");
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(
                User.builder().username("student").role("STUDENT").build()));
        when(loginProtection.verify(any(BooleanSupplier.class))).thenReturn(true);
        when(jwtService.generateToken("student", "STUDENT")).thenThrow(new IllegalStateException("token error"));
        assertThrows(IllegalStateException.class, () -> service.login(request));
        verify(loginProtection, never()).clearFailures(anyString());
    }

    @Test
    void returnsNewProfileFieldsAndAllowsExistingNullFields() {
        LocalDate dob = LocalDate.of(2010, 5, 6);
        User user = User.builder().id(UUID.randomUUID()).username("student001").role("STUDENT")
                .studentIrn("IRN-001").passwordMock("practice-123").dob(dob).gender("FEMALE").build();
        when(userRepository.findByUsername("student001")).thenReturn(Optional.of(user));

        UserProfileResponse response = service.getProfile("student001", List.of("STUDENT"));

        assertEquals("IRN-001", response.getStudentIrn());
        assertEquals("practice-123", response.getPasswordMock());
        assertEquals(dob, response.getDob());
        assertEquals("FEMALE", response.getGender());

        User existing = User.builder().id(UUID.randomUUID()).username("legacy").role("STUDENT").build();
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(existing));
        UserProfileResponse legacyResponse = service.getProfile("legacy", List.of("STUDENT"));
        assertNull(legacyResponse.getStudentIrn());
        assertNull(legacyResponse.getPasswordMock());
        assertNull(legacyResponse.getDob());
        assertNull(legacyResponse.getGender());
    }

    @Test
    void acceptsAndReturnsDobAsDayMonthYear() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StudentCreateRequest request = mapper.readValue(
                "{\"passwordMock\":\"practice-123\",\"dob\":\"06/05/2010\"}",
                StudentCreateRequest.class);
        UserProfileResponse response = UserProfileResponse.builder()
                .dob(request.getDob()).build();

        assertEquals(LocalDate.of(2010, 5, 6), request.getDob());
        assertEquals("06/05/2010", mapper.readTree(mapper.writeValueAsString(response))
                .get("dob").asText());
    }
}
