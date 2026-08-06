package com.ecommerce.project.repositories;

import com.ecommerce.project.model.RefreshToken;
import com.ecommerce.project.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Find a refresh token by its string value (used during /refresh-token endpoint)
    Optional<RefreshToken> findByToken(String token);

    // Delete all refresh tokens for a user (used during logout)
    @Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    int deleteByUser(User user);

    Optional<RefreshToken> findByUser(User user);
}
