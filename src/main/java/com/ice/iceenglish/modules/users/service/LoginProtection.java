package com.ice.iceenglish.modules.users.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Bảo vệ đăng nhập: (1) giới hạn số lượt băm BCrypt chạy song song để không chiếm hết CPU và luồng Tomcat
 * của các API khác khi cả lớp đăng nhập cùng lúc; (2) tạm khoá theo tài khoản sau nhiều lần sai mật khẩu liên tiếp.
 */
@Component
public class LoginProtection {

    private final Semaphore hashingSlots;
    private final long hashingWaitSeconds;
    private final int maxFailures;
    private final Cache<String, AtomicInteger> failures;

    public LoginProtection(
            @Value("${app.security.login.max-concurrent-hashing:0}") int maxConcurrentHashing,
            @Value("${app.security.login.hashing-wait-seconds:60}") long hashingWaitSeconds,
            @Value("${app.security.login.max-failures:10}") int maxFailures,
            @Value("${app.security.login.lock-seconds:300}") long lockSeconds) {
        int slots = maxConcurrentHashing > 0 ? maxConcurrentHashing : Runtime.getRuntime().availableProcessors();
        this.hashingSlots = new Semaphore(slots, true);
        this.hashingWaitSeconds = hashingWaitSeconds;
        this.maxFailures = maxFailures;
        this.failures = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofSeconds(lockSeconds))
                .build();
    }

    /** Ném 429 nếu tài khoản đang bị tạm khoá do nhập sai quá nhiều lần. */
    public void assertNotLocked(String username) {
        AtomicInteger count = failures.getIfPresent(key(username));
        if (count != null && count.get() >= maxFailures) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau ít phút.");
        }
    }

    /** Chạy phép so khớp mật khẩu trong giới hạn song song; quá thời gian chờ thì trả 503 thay vì treo lâu. */
    public boolean verify(BooleanSupplier passwordMatches) {
        boolean acquired;
        try {
            acquired = hashingSlots.tryAcquire(hashingWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống đang bận, vui lòng thử lại.");
        }
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống đang bận, vui lòng thử lại.");
        }
        try {
            return passwordMatches.getAsBoolean();
        } finally {
            hashingSlots.release();
        }
    }

    public void recordFailure(String username) {
        failures.get(key(username), k -> new AtomicInteger()).incrementAndGet();
    }

    public void clearFailures(String username) {
        failures.invalidate(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
