package com.ice.iceenglish.core.security;

import com.ice.iceenglish.modules.users.entity.User;
import com.ice.iceenglish.modules.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache ngắn hạn username -> role để filter JWT không phải truy vấn DB ở mọi request.
 * Kiểm tra trạng thái hoạt động trong DB ở mỗi request; thay đổi role có hiệu lực sau tối đa {@code ttl} giây.
 */
@Component
public class UserRoleCache {

    private static final int MAX_ENTRIES = 20_000;

    private record Entry(String role, long expiresAtNanos) {}

    private final UserRepository userRepository;
    private final long ttlNanos;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public UserRoleCache(UserRepository userRepository,
                         @Value("${app.security.role-cache-ttl-seconds:60}") long ttlSeconds) {
        this.userRepository = userRepository;
        this.ttlNanos = ttlSeconds * 1_000_000_000L;
    }

    /** Role thô của user đang hoạt động, rỗng nếu user không tồn tại hoặc bị vô hiệu hóa. */
    public Optional<String> findRole(String username) {
        long now = System.nanoTime();
        Entry cached = cache.get(username);
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            if (userRepository.existsByUsernameAndIsActiveTrue(username)) {
                return Optional.of(cached.role());
            }
            cache.remove(username, cached);
            return Optional.empty();
        }
        Optional<String> role = userRepository.findByUsername(username)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .map(User::getRole);
        if (role.isPresent() && ttlNanos > 0) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(username, new Entry(role.get(), now + ttlNanos));
        } else {
            cache.remove(username);
        }
        return role;
    }
}
