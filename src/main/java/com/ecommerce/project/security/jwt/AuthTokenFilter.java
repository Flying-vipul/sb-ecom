package com.ecommerce.project.security.jwt;

import com.ecommerce.project.service.TokenBlacklistService;
import com.ecommerce.project.service.UserDetailsImpl;
import com.ecommerce.project.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter — runs on EVERY HTTP request exactly once.
 *
 * PRODUCTION OPTIMIZATIONS APPLIED:
 * 1. Reads userId, email, roles directly from JWT claims → ZERO DB calls per request
 * 2. Checks Redis blacklist → rejects tokens that were explicitly logged out
 * 3. Falls back to DB-based loading only if claims are missing (e.g., OAuth2 tokens)
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    // Only used as fallback for tokens without embedded claims (e.g., OAuth2)
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    // Redis blacklist — checks if user has logged out
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        logger.debug("AuthTokenFilter called for URI: {}", request.getRequestURI());

        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {

                // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                // STEP 1: Redis blacklist check — FAIL SECURE
                // TokenBlacklistService.isBlacklisted() returns TRUE
                // if Redis is DOWN — so blacklisted tokens are ALWAYS
                // rejected even during a Redis outage.
                // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                if (tokenBlacklistService.isBlacklisted(jwt)) {
                    logger.warn("Rejected blacklisted token for URI: {}", request.getRequestURI());
                    // Do NOT set authentication — filter chain continues
                    // AuthorizationFilter will see no auth → 401
                    filterChain.doFilter(request, response);
                    return;
                }

                // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                // STEP 2: Extract claims from JWT — ZERO DB CALLS
                // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                String username  = jwtUtils.getUsernameByJwtTokens(jwt);
                Long   id        = jwtUtils.getIdFromJwt(jwt);
                String email     = jwtUtils.getEmailFromJwt(jwt);
                List<String> roles = jwtUtils.getRolesFromJwt(jwt);

                UsernamePasswordAuthenticationToken authentication;

                if (id != null && email != null && roles != null) {
                    // ✅ FAST PATH: All claims present — build UserDetailsImpl from JWT
                    // No database query at all
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UserDetailsImpl userDetails = new UserDetailsImpl(
                            id, username, email, null, authorities
                    );

                    authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    logger.debug("JWT claims path — no DB call for user: {}", username);

                } else {
                    // FALLBACK PATH: Claims missing (OAuth2 token or legacy token)
                    // Load from DB — this is the old behaviour
                    var userDetails = userDetailsService.loadUserByUsername(username);
                    authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    logger.debug("DB fallback path for user: {}", username);
                }

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set authentication into SecurityContextHolder for this request thread
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Roles from JWT: {}", authentication.getAuthorities());
            }

        } catch (Exception e) {
            // JWT parsing/validation failed — log and continue without auth
            // AuthorizationFilter will handle the 401
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Reads JWT from Authorization: Bearer header.
     */
    private String parseJwt(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromHeader(request);
        logger.debug("AuthTokenFilter.parseJwt: {}", jwt != null ? "token found" : "no token");
        return jwt;
    }
}
