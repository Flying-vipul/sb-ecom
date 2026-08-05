package com.ecommerce.project.security.jwt;

import com.ecommerce.project.service.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${spring.app.jwtSecret}")
    private String jwtSecret;

    @Value("${spring.app.jwtExpirationMs:86400000}")
    private int jwtExpirationMs;

    @Value("${spring.ecom.app.jwtCookieName:ecom-jwt}")
    private String jwtCookie;

    // ==========================================
    // TOKEN EXTRACTION FROM REQUEST
    // ==========================================

    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        logger.debug("Authorization Header: {} ", bearerToken);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public String getJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        if (cookie != null) {
            return cookie.getValue();
        }
        return null;
    }

    // ==========================================
    // COOKIE HELPERS
    // ==========================================

    public ResponseCookie generateJwtCookie(UserDetailsImpl userPrincipal) {
        String jwt = generateToken(userPrincipal);
        String cookieName = jwtCookie != null ? jwtCookie : "ecom-cookie";
        return ResponseCookie.from(cookieName, jwt)
                .path("/api")
                .maxAge(24 * 60 * 60)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
    }

    public ResponseCookie getCleanJwtCookie() {
        String cookieName = jwtCookie != null ? jwtCookie : "ecom-cookie";
        return ResponseCookie.from(cookieName, "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
    }

    public ResponseCookie generateRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("ecom-refresh-token", refreshToken)
                .path("/api/auth/refresh-token")
                .maxAge(7 * 24 * 60 * 60)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
    }

    public ResponseCookie getCleanRefreshTokenCookie() {
        return ResponseCookie.from("ecom-refresh-token", "")
                .path("/api/auth/refresh-token")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();
    }

    // ==========================================
    // TOKEN GENERATION
    // ==========================================

    public String generateToken(UserDetailsImpl userPrincipal) {
        List<String> roles = userPrincipal.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList();

        var builder = Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs));

        if (userPrincipal.getId() != null) {
            builder.claim("id", userPrincipal.getId());
        }
        if (userPrincipal.getEmail() != null) {
            builder.claim("email", userPrincipal.getEmail());
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", roles);
        }

        return builder.signWith(key()).compact();
    }

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    // ==========================================
    // CLAIM EXTRACTORS
    // ==========================================

    public String getUsernameByJwtTokens(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getIdFromJwt(String token) {
        Object id = parseClaims(token).get("id");
        if (id == null) return null;
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        return Long.parseLong(id.toString());
    }

    public String getEmailFromJwt(String token) {
        return parseClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromJwt(String token) {
        return (List<String>) parseClaims(token).get("roles");
    }

    public Date getExpirationFromJwt(String token) {
        return parseClaims(token).getExpiration();
    }

    // ==========================================
    // VALIDATION & HELPERS
    // ==========================================

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) key())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT Token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT Token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public Key key() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e1) {
            try {
                keyBytes = Decoders.BASE64URL.decode(jwtSecret);
            } catch (Exception e2) {
                keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT Secret key must be at least 32 characters or 256 bits long!");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}