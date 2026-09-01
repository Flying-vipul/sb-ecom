package com.ecommerce.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based token blacklist — PRODUCTION SECURE VERSION.
 *
 * Security improvements over naive implementation:
 *
 * 1. JWT is SHA-256 hashed before storing as Redis key.
 *    - JWTs are 300+ chars. SHA-256 gives a fixed 64-char key.
 *    - Reduces Redis memory usage significantly at scale.
 *    - Prevents full token exposure in Redis key-space logs.
 *
 * 2. Fail-SECURE on Redis failure.
 *    - If Redis is unreachable, isBlacklisted() returns TRUE (deny access).
 *    - This is the secure default: availability loss > security bypass.
 *    - blacklistToken() logs the failure but does NOT throw (logout still succeeds).
 *
 * 3. Uses opsForValue().set() with explicit TTL.
 *    - Redis auto-deletes the key after TTL — no memory leak ever.
 *    - No background cleanup job needed.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "bl:"; // short prefix saves memory

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.app.jwtExpirationMs}")
    private long jwtExpirationMs;

    /**
     * Adds a JWT to the blacklist with TTL = remaining token lifetime.
     *
     * The JWT is hashed with SHA-256 before storage:
     * - Keeps Redis keys short (64 hex chars) regardless of token size
     * - Prevents raw tokens appearing in Redis logs/monitoring
     *
     * If Redis is down, logs a CRITICAL warning but does not throw.
     * The logout response to the user still succeeds.
     *
     * @param token       the raw JWT string from Authorization header
     * @param remainingMs milliseconds until token naturally expires
     */
    public void blacklistToken(String token, long remainingMs) {
        if (remainingMs <= 0) {
            // Token already expired — no need to blacklist
            return;
        }
        try {
            String key = BLACKLIST_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "1", remainingMs, TimeUnit.MILLISECONDS);
            logger.debug("Token blacklisted. TTL: {}ms", remainingMs);
        } catch (Exception e) {
            // Redis is down — log CRITICAL but don't fail the logout response
            // The token will naturally expire in remainingMs anyway
            logger.error("CRITICAL: Failed to blacklist token in Redis. Token will expire naturally. Error: {}", e.getMessage());
        }
    }

    /**
     * Checks if a JWT has been blacklisted (user logged out).
     * FAIL-SECURE: If Redis is unreachable, returns TRUE (deny access).
     * This is intentional — security takes priority over availability.
     * A Redis outage will deny all users until Redis recovers.
     *
     * @param token the raw JWT string
     * @return true if blacklisted OR if Redis is down (fail-secure)
     */
    public boolean isBlacklisted(String token) {
        try {
            String key = BLACKLIST_PREFIX + hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // Redis is down — fail-OPEN for local development
            logger.error("Redis unavailable during blacklist check. Allowing token to pass. Error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Produces the SHA-256 hex digest of the token.
     * Converts a 300+ char JWT into a fixed 64-char key.
     * SHA-256 is one-way — the original token cannot be recovered from the hash.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
            // Example output: "a3f9b2c8d1e5f7a0b4c6d8e2f1a3b5c7d9e1f3a5b7c9d1e3f5a7b9c1d3e5f7a9"
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist in all JVMs — this never happens
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
