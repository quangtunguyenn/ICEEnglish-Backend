package com.ice.iceenglish.modules.users.service;

import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentManagementServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final StudentManagementService service = new StudentManagementService(repository);

    @Test
    void searchesWithOrWithoutAccentsAndCombinesStatus() {
        User active = User.builder().fullName("Đặng Thị Ánh").username("anh").build();
        User inactive = User.builder().fullName("Dang Thi Binh").username("binh").isActive(false).build();
        when(repository.findAllStudents()).thenReturn(List.of(active, inactive));
        assertEquals(List.of("anh", "binh"), service.list(null, "đặng   THỊ").stream()
                .map(item -> item.username()).toList());
        assertEquals(List.of("anh"), service.list(true, "dang thi").stream()
                .map(item -> item.username()).toList());
        assertEquals(List.of("binh"), service.list(false, "DANG THI").stream()
                .map(item -> item.username()).toList());
        assertTrue(service.list(null, "khong co").isEmpty());
    }

    @Test
    void updatesOnlyStudentsAndValidatesStatus() {
        UUID id = UUID.randomUUID();
        User student = User.builder().id(id).role("STUDENT").build();
        when(repository.findStudentById(id)).thenReturn(Optional.of(student));
        assertFalse(service.setActive(id, false).isActive());
        assertTrue(service.setActive(id, true).isActive());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.setActive(id, null))
                .getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.setActive(UUID.randomUUID(), false))
                .getStatusCode().value());
    }
}
