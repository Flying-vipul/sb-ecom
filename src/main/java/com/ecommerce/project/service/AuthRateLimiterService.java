package com.ecommerce.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiter for auth endpoints.
 *
 * Implements two layers of protection:
 * 1. Per-Account Rate Limiting: Blocks brute-force attacks on specific usernames/emails.
 *    If 5 failed attempts occur on an account within 15 minutes, only that account is locked for 15 minutes.
 *    Other users are completely unaffected!
 * 2. Per-IP Rate Limiting: Blocks distributed attacks from a single IP across many accounts.
 *    Only applies to PUBLIC IPs (ignores private/Docker bridge IPs such as 172.16.0.0/12, 10.0.0.0/8, 127.0.0.1)
 *    so deploying inside Docker/Oracle VM does not cause collateral lockout of other users.
 */
@Service
public class AuthRateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(AuthRateLimiterService.class);

    // Account limits: 5 failed attempts per 15 min -> 15 min lock
    private static final int MAX_ACCOUNT_ATTEMPTS = 5;
    private static final long ACCOUNT_WINDOW_SECONDS = 900;
    private static final long ACCOUNT_BLOCK_DURATION_SECONDS = 900;

    // IP limits (public IPs only): 25 failed attempts per 15 min -> 15 min lock
    private static final int MAX_IP_ATTEMPTS = 25;
    private static final long IP_WINDOW_SECONDS = 900;
    private static final long IP_BLOCK_DURATION_SECONDS = 900;

    private static final String ACCOUNT_ATTEMPT_PREFIX = "rl:attempt:user:";
    private static final String ACCOUNT_BLOCK_PREFIX   = "rl:block:user:";
    private static final String IP_ATTEMPT_PREFIX      = "rl:attempt:ip:";
    private static final String IP_BLOCK_PREFIX        = "rl:block:ip:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==========================================
    // ACCOUNT-LEVEL RATE LIMITING (Primary)
    // ==========================================

    public void recordFailedAccountAttempt(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return;
        String normalized = usernameOrEmail.toLowerCase().trim();
        try {
            String attemptKey = ACCOUNT_ATTEMPT_PREFIX + normalized;
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);

            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptKey, ACCOUNT_WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            if (attempts != null && attempts >= MAX_ACCOUNT_ATTEMPTS) {
                String blockKey = ACCOUNT_BLOCK_PREFIX + normalized;
                redisTemplate.opsForValue().set(blockKey, "blocked", ACCOUNT_BLOCK_DURATION_SECONDS, TimeUnit.SECONDS);
                redisTemplate.delete(attemptKey);
                logger.warn("Account [{}] temporarily locked for {} minutes after {} failed login attempts",
                        normalized, ACCOUNT_BLOCK_DURATION_SECONDS / 60, MAX_ACCOUNT_ATTEMPTS);
            }
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (recordFailedAccountAttempt): {}", e.getMessage());
        }
    }

    public boolean isAccountBlocked(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return false;
        String normalized = usernameOrEmail.toLowerCase().trim();
        try {
            String blockKey = ACCOUNT_BLOCK_PREFIX + normalized;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (isAccountBlocked): {}", e.getMessage());
            return false;
        }
    }

    public void clearAccountAttempts(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return;
        String normalized = usernameOrEmail.toLowerCase().trim();
        try {
            redisTemplate.delete(ACCOUNT_ATTEMPT_PREFIX + normalized);
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (clearAccountAttempts): {}", e.getMessage());
        }
    }

    public long getAccountBlockRemainingSeconds(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return 0;
        String normalized = usernameOrEmail.toLowerCase().trim();
        try {
            Long ttl = redisTemplate.getExpire(ACCOUNT_BLOCK_PREFIX + normalized, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==========================================
    // IP-LEVEL RATE LIMITING (Secondary - Public IPs only)
    // ==========================================

    public void recordFailedIpAttempt(String ip) {
        if (ip == null || ip.isBlank() || isInternalOrPrivateIp(ip)) {
            return; // Never block Docker container internal or private IPs
        }
        try {
            String attemptKey = IP_ATTEMPT_PREFIX + ip;
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);

            if (attempts != null && attempts == 1) {
                redisTemplate.expire(attemptKey, IP_WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            if (attempts != null && attempts >= MAX_IP_ATTEMPTS) {
                String blockKey = IP_BLOCK_PREFIX + ip;
                redisTemplate.opsForValue().set(blockKey, "blocked", IP_BLOCK_DURATION_SECONDS, TimeUnit.SECONDS);
                redisTemplate.delete(attemptKey);
                logger.warn("Public IP [{}] blocked for {} minutes after {} failed attempts",
                        ip, IP_BLOCK_DURATION_SECONDS / 60, MAX_IP_ATTEMPTS);
            }
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (recordFailedIpAttempt): {}", e.getMessage());
        }
    }

    public boolean isIpBlocked(String ip) {
        if (ip == null || ip.isBlank() || isInternalOrPrivateIp(ip)) {
            return false;
        }
        try {
            String blockKey = IP_BLOCK_PREFIX + ip;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey));
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (isIpBlocked): {}", e.getMessage());
            return false;
        }
    }

    public void clearIpAttempts(String ip) {
        if (ip == null || ip.isBlank() || isInternalOrPrivateIp(ip)) return;
        try {
            redisTemplate.delete(IP_ATTEMPT_PREFIX + ip);
        } catch (Exception e) {
            logger.error("Rate limiter Redis error (clearIpAttempts): {}", e.getMessage());
        }
    }

    public long getIpBlockRemainingSeconds(String ip) {
        if (ip == null || ip.isBlank() || isInternalOrPrivateIp(ip)) return 0;
        try {
            Long ttl = redisTemplate.getExpire(IP_BLOCK_PREFIX + ip, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Checks if an IP is local, loopback, or private (e.g. Docker bridge 172.17.x, 172.18.x).
     * These should NEVER be blocked, otherwise all users routed through Docker will be locked out.
     */
    public boolean isInternalOrPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        String cleanIp = ip.trim();
        if ("127.0.0.1".equals(cleanIp) || "localhost".equalsIgnoreCase(cleanIp)
                || "0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp)) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(cleanIp);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            // If invalid or unparseable IP, treat as internal to be safe
            return true;
        }
    }

    // ==========================================
    // BACKWARD COMPATIBLE METHODS (delegates to IP)
    // ==========================================

    public void recordFailedAttempt(String ip) {
        recordFailedIpAttempt(ip);
    }

    public boolean isBlocked(String ip) {
        return isIpBlocked(ip);
    }

    public void clearAttempts(String ip) {
        clearIpAttempts(ip);
    }

    public long getBlockRemainingSeconds(String ip) {
        return getIpBlockRemainingSeconds(ip);
    }
}
