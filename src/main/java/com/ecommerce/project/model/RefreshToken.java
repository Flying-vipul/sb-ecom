package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The actual token string (UUID — long and random, not guessable)
    @Column(nullable = false, unique = true)
    private String token;

    // Which user owns this refresh token
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private User user;

    // When this refresh token expires (7 days from creation)
    @Column(nullable = false)
    private Instant expiryDate;
}
