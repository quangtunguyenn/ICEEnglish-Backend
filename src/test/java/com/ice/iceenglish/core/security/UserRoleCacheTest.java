package com.ice.iceenglish.core.security;

import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRoleCacheTest {
    @Test
    void cachedTokenStopsWorkingOnDeactivationAndWorksAgainAfterReactivation() {
        UserRepository repository = mock(UserRepository.class);
        User user = User.builder().username("student").role("STUDENT").build();
        when(repository.findByUsername("student")).thenReturn(Optional.of(user));
        when(repository.existsByUsernameAndIsActiveTrue("student")).thenReturn(true, false);
        UserRoleCache cache = new UserRoleCache(repository, 60);
        assertEquals("STUDENT", cache.findRole("student").orElseThrow());
        assertEquals("STUDENT", cache.findRole("student").orElseThrow());
        user.setIsActive(false);
        assertTrue(cache.findRole("student").isEmpty());
        assertTrue(cache.findRole("student").isEmpty());
        user.setIsActive(true);
        assertEquals("STUDENT", cache.findRole("student").orElseThrow());
    }
}
