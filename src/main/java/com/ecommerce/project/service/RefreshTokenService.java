package com.ecommerce.project.service;

import com.ecommerce.project.model.RefreshToken;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.RefreshTokenRepository;
import com.ecommerce.project.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    // How long refresh tokens last — 7 days (604800000 ms), from application.properties
    @Value("${spring.app.refreshTokenExpirationMs}")
    private Long refreshTokenExpirationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Creates a new refresh token for a user.
     * If the user already has one, it is deleted and replaced.
     * This ensures one active refresh token per user at all times.
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // If the user already has a token, reuse the row to avoid unique constraint violations
        Optional<RefreshToken> existing = refreshTokenRepository.findByUser(user);
        RefreshToken refreshToken = existing.orElseGet(RefreshToken::new);
        
        refreshToken.setUser(user);
        // UUID is cryptographically random, long, and unguessable
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Finds a refresh token by its string value.
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Checks if a refresh token has expired.
     * If expired, deletes it from DB and throws exception.
     */
    public RefreshToken verifyExpiry(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException(
                "Refresh token expired. Please log in again."
            );
        }
        return token;
    }

    /**
     * Deletes all refresh tokens for a given user.
     * Called on logout.
     */
    @Transactional
    public void deleteByUsername(String username) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        refreshTokenRepository.deleteByUser(user);
    }
}
