package com.ecommerce.project.util;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    @Autowired
    UserRepository userRepository;

    /**
     * Returns the logged-in user's email.
     *
     * OPTIMIZED: Reads directly from UserDetailsImpl in SecurityContextHolder.
     * Since email is now embedded in JWT claims and stored in UserDetailsImpl,
     * this is ZERO additional DB calls.
     *
     * Falls back to DB lookup if principal is not a UserDetailsImpl
     * (e.g., anonymous or OAuth2 fallback path).
     */
    public String loggedInEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            // Fast path: email already in UserDetailsImpl from JWT claims
            return userDetails.getEmail();
        }

        // Fallback: load from DB (only for non-UserDetailsImpl principals)
        User user = userRepository.findByUserName(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User Not Found: " + authentication.getName()));
        return user.getEmail();
    }

    /**
     * Returns the logged-in user's ID.
     *
     * OPTIMIZED: Reads directly from UserDetailsImpl in SecurityContextHolder.
     * Zero DB calls.
     */
    public Long loggedInUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            // Fast path: id already in UserDetailsImpl from JWT claims
            return userDetails.getId();
        }

        // Fallback: load from DB
        User user = userRepository.findByUserName(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User Not Found: " + authentication.getName()));
        return user.getUserId();
    }

    /**
     * Returns the full logged-in User entity from DB.
     *
     * NOTE: This ALWAYS hits DB — a full User entity is needed
     * (with addresses, cart, products relationships) and cannot
     * be stored in JWT. This is intentional and unavoidable.
     * However, it now uses the username from UserDetailsImpl
     * instead of the Authentication name, which is consistent.
     */
    public User loggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getPrincipal() instanceof UserDetailsImpl userDetails
                ? userDetails.getUsername()
                : authentication.getName();

        return userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User Not Found: " + username));
    }
}