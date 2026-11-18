package com.videototext.security.ratelimit;

import com.videototext.security.audit.SecurityAuditService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final SecurityAuditService securityAuditService;

    @Getter
    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Getter
    @Value("${app.rate-limit.auth-limit:20}")
    private int authLimit;

    @Getter
    @Value("${app.rate-limit.upload-limit:15}")
    private int uploadLimit;

    @Getter
    @Value("${app.rate-limit.default-limit:120}")
    private int defaultLimit;

    @Getter
    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    private final Map<String, SlidingWindowBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult tryAcquire(String clientKey, String requestUri) {
        if (!enabled) {
            return RateLimitResult.ok(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        int limit = resolveLimit(requestUri);
        long windowMillis = windowSeconds * 1000L;
        String bucketKey = resolveBucketCategory(requestUri) + ":" + clientKey;

        SlidingWindowBucket bucket = buckets.computeIfAbsent(
                bucketKey,
                k -> new SlidingWindowBucket(limit, windowMillis)
        );

        long now = System.currentTimeMillis();
        boolean allowed = bucket.tryAcquire(now);
        int remaining = bucket.getRemaining(now);

        if (!allowed) {
            log.warn("Rate limit exceeded for clientKey={} on uri={} (limit={}/{}s)",
                    clientKey, requestUri, limit, windowSeconds);
            securityAuditService.logSecurityEvent(
                    "RATE_LIMIT_EXCEEDED",
                    clientKey,
                    null,
                    null,
                    "DENIED",
                    String.format("Exceeded %d req/%ds on %s", limit, windowSeconds, requestUri)
            );
            return RateLimitResult.rateLimited(limit, windowSeconds);
        }

        return RateLimitResult.ok(limit, remaining);
    }

    public int resolveLimit(String requestUri) {
        if (requestUri == null) {
            return defaultLimit;
        }
        if (requestUri.startsWith("/api/v1/auth/")) {
            return authLimit;
        }
        if (requestUri.contains("/upload") || requestUri.contains("/multipart")) {
            return uploadLimit;
        }
        return defaultLimit;
    }

    public String resolveBucketCategory(String requestUri) {
        if (requestUri == null) {
            return "DEFAULT";
        }
        if (requestUri.startsWith("/api/v1/auth/")) {
            return "AUTH";
        }
        if (requestUri.contains("/upload") || requestUri.contains("/multipart")) {
            return "UPLOAD";
        }
        return "DEFAULT";
    }

    public void clear() {
        buckets.clear();
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupIdleBuckets() {
        long now = System.currentTimeMillis();
        long idleThreshold = windowSeconds * 5 * 1000L;
        buckets.entrySet().removeIf(entry -> entry.getValue().isIdle(now, idleThreshold));
    }

    private static class SlidingWindowBucket {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private final int limit;
        private final long windowMillis;

        public SlidingWindowBucket(int limit, long windowMillis) {
            this.limit = limit;
            this.windowMillis = windowMillis;
        }

        public synchronized boolean tryAcquire(long now) {
            evictOlderThan(now - windowMillis);
            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return true;
            }
            return false;
        }

        public synchronized int getRemaining(long now) {
            evictOlderThan(now - windowMillis);
            return Math.max(0, limit - timestamps.size());
        }

        public synchronized boolean isIdle(long now, long idleThreshold) {
            return timestamps.isEmpty() || (now - timestamps.peekLast() > idleThreshold);
        }

        private void evictOlderThan(long cutoff) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
        }
    }
}
