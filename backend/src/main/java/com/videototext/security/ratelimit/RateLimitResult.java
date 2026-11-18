package com.videototext.security.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private int limit;
    private int remaining;
    private long retryAfterSeconds;

    public static RateLimitResult ok(int limit, int remaining) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(remaining)
                .retryAfterSeconds(0)
                .build();
    }

    public static RateLimitResult rateLimited(int limit, long retryAfterSeconds) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(limit)
                .remaining(0)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}
