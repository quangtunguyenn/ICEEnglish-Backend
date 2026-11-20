package com.ice.iceenglish.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSigningKeyTest {
    @Test
    void validatesKeyBeforeServingRequests() {
        JwtService service = new JwtService(Clock.systemUTC());
        ReflectionTestUtils.setField(service, "secretKey",
                Base64.getEncoder().encodeToString(new byte[32]));
        assertDoesNotThrow(service::validateSigningKey);

        ReflectionTestUtils.setField(service, "secretKey", Base64.getEncoder().encodeToString(new byte[8]));
        assertThrows(RuntimeException.class, service::validateSigningKey);
    }
}
