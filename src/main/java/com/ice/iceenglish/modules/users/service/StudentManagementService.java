package com.ice.iceenglish.modules.users.service;

import com.ice.iceenglish.core.text.SearchText;
import com.ice.iceenglish.modules.users.dto.StudentResponse;
import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentManagementService {
    private final UserRepository repository;

    @Transactional(readOnly = true)
    public List<StudentResponse> list(Boolean isActive, String keyword) {
        String normalized = SearchText.normalize(keyword);
        return repository.findAllStudents().stream()
                .filter(user -> isActive == null || isActive.equals(user.getIsActive()))
                .filter(user -> normalized.isEmpty() || SearchText.normalize(user.getFullName()).contains(normalized))
                .map(StudentResponse::from)
                .toList();
    }

    @Transactional
    public StudentResponse setActive(UUID id, Boolean isActive) {
        if (isActive == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isActive là bắt buộc.");
        }
        User user = repository.findStudentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh."));
        user.setIsActive(isActive);
        return StudentResponse.from(user);
    }
}
