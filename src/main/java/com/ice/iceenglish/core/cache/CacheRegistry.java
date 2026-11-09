package com.ice.iceenglish.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Nơi tạo và xoá các cache nội dung (đề thi, video, từ vựng...). Nội dung này chỉ thay đổi khi giáo viên
 * import/publish/archive nên mọi thao tác ghi gọi {@link #evictAllAfterCommit()} để xoá toàn bộ cache.
 * TTL là lưới an toàn khi chạy nhiều instance (việc xoá cache không lan sang instance khác).
 */
@Component
public class CacheRegistry {

    private final Duration ttl;
    private final TransactionTemplate readOnlyTx;
    private final List<Cache<?, ?>> caches = new CopyOnWriteArrayList<>();

    public CacheRegistry(@Value("${app.cache.ttl-seconds:120}") long ttlSeconds,
                         PlatformTransactionManager transactionManager) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    public <K, V> Cache<K, V> create(long maxEntries) {
        Cache<K, V> cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttl)
                .build();
        caches.add(cache);
        return cache;
    }

    /**
     * Lấy từ cache; nếu chưa có thì chạy {@code loader} trong transaction chỉ đọc. Cache hit không mở
     * transaction nên không chiếm kết nối DB. Mỗi key chỉ một luồng nạp, lỗi (vd 404) không bị cache.
     */
    public <K, V> V getOrLoad(Cache<K, V> cache, K key, Supplier<V> loader) {
        return cache.get(key, k -> readOnlyTx.execute(status -> loader.get()));
    }

    /** Xoá sau khi transaction ghi commit để request khác không nạp lại dữ liệu cũ vào cache. */
    public void evictAllAfterCommit() {
        afterCommit(this::evictAll);
    }

    /** Chỉ xoá các cache được chỉ định (vd. nội dung bài đọc), giữ nguyên cache đề thi/video. */
    public void evictAfterCommit(Cache<?, ?>... targets) {
        afterCommit(() -> List.of(targets).forEach(Cache::invalidateAll));
    }

    /** Chạy {@code query} trong transaction chỉ đọc (dùng cho truy vấn không đi qua cache). */
    public <V> V callReadOnly(Supplier<V> query) {
        return readOnlyTx.execute(status -> query.get());
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public void evictAll() {
        caches.forEach(Cache::invalidateAll);
    }
}
