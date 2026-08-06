package com.ecommerce.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiter for auth endpoints.
 *
 * Uses a sliding counter per IP address.
 * Each failed login attempt increments a counter with a TTL window.
 * After MAX_ATTEMPTS, the IP is blocked for BLOCK_DURATION_SECONDS.
 *
 * Why Redis?
 * - Atomic INCR operation prevents race conditions (two requests at same time)
 * - TTL auto-expires counters — no cleanup needed
 * - Survives app restarts — state is in Redis, not in-memory
 */
@Service
public class AuthRateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(AuthRateLimiterService.class);

    // Allow 5 failed attempts per window
    private static final int MAX_ATTEMPTS = 5;

    // Window duration: 15 minutes
    private static final long WINDOW_SECONDS = 900;

    // Block duration after exceeding limit: 30 minutes
    private static final long BLOCK_DURATION_SECONDS = 1800;

    private static final String ATTEMPT_PREFIX = "rl:attempt:";
    private static final String BLOCK_PREFIX   = "rl:block:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Records a failed login attempt for the given IP.
     * If 5 attempts in 15 min → blocks IP for 30 min.
     *
     * @param ip the client IP address
     */
    public void recordFailedAttempt(String ip) {
        try {
            String attemptKey = ATTEMPT_PREFIX + ip;

            // Atomically increment counter
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);

            // Set TTL on first attempt (sliding window)
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            // If exceeded limit → block this IP
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                String blockKey = BLOCK_PREFIX + ip;
                redisTemplate.opsForValue().set(blockKey, "blocked", BLOCK_DURATION_SECONDS, TimeUnit.SECONDS);
                redisTemplate.delete(attemptKey); // reset attempt counter
                logger.warn("IP {} blocked for {} minutes after {} failed login attempts",
                        ip, BLOCK_DURATION_SECONDS / 60, MAX_ATTEMPTS);
            }
        } catch (Exception e) {
            // Redis down — log but don't break login
            logger.error("Rate limiter Redis error (recordFailedAttempt): {}", e.getMessage());
        }
    }

    /**
     * Checks if an IP is currently blocked.
     *
     * @param ip the client IP address
     * @return true if blocked (deny login attempt)
     */
    public boolean isBlocked(String ip) {
        try {
            String blockKey = BLOCK_PREFIX + ip;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
        } catch (Exception e) {
            // Redis down — fail OPEN here (allow login attempts)
            // Better to allow logins than lock out all users during Redis outage
            logger.error("Rate limiter Redis error (isBlocked): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clears the failed attempt counter for an IP after successful login.
     * Prevents a user from being blocked after a few mistakes then succeeding.
     *
     * @param ip the client IP address
     */
    public void clearAttempts(String ip) {
        try {
            redisTemplate.delete(ATTEMPT_PREFIX + ip);
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (clearAttempts): {}", e.getMessage());
        }
    }

    /**
     * Returns how many seconds remain on the block for the given IP.
     * Used to tell the user how long to wait.
     *
     * @param ip the client IP address
     * @return seconds remaining, or 0 if not blocked
     */
    public long getBlockRemainingSeconds(String ip) {
        try {
            Long ttl = redisTemplate.getExpire(BLOCK_PREFIX + ip, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
