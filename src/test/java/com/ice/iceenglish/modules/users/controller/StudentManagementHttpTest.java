package com.ice.iceenglish.modules.users.controller;

import com.ice.iceenglish.config.SecurityConfig;
import com.ice.iceenglish.core.security.JwtAuthenticationFilter;
import com.ice.iceenglish.core.security.JwtService;
import com.ice.iceenglish.core.security.UserRoleCache;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import com.ice.iceenglish.modules.users.service.AuthService;
import com.ice.iceenglish.modules.users.service.StudentManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, StudentManagementController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, UserRoleCache.class, StudentManagementService.class})
class StudentManagementHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository repository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AuthService authService;

    @Test
    void anonymousCannotManageStudents() throws Exception {
        mvc.perform(get("/api/v1/admin/students")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", UUID.randomUUID())
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "TEACHER")
    void teacherCannotManageStudents() throws Exception {
        mvc.perform(get("/api/v1/admin/students")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", UUID.randomUUID())
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanListAndChangeStatusWithoutReturningPasswordHash() throws Exception {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).username("anh").fullName("Đặng Thị Ánh")
                .role("STUDENT").passwordHash("hash").build();
        when(repository.findAllStudents()).thenReturn(List.of(user));
        when(repository.findStudentById(id)).thenReturn(Optional.of(user));
        mvc.perform(get("/api/v1/admin/students").param("isActive", "true").param("keyword", "dang thi"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", id)
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", id)
                .contentType("application/json").content("{\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(true));
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", id)
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/students/{id}/is-active", UUID.randomUUID())
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/students").param("isActive", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveUserCannotUsePreviouslyCachedJwt() throws Exception {
        User user = User.builder().username("student").role("STUDENT").build();
        when(jwtService.extractUsername("token")).thenReturn("student");
        when(repository.findByUsername("student")).thenReturn(Optional.of(user));
        when(repository.existsByUsernameAndIsActiveTrue("student")).thenReturn(true, false);
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/profile").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
    }
}
