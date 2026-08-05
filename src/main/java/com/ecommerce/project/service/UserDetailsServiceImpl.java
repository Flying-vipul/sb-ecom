package com.ecommerce.project.service;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // Try to find by username first, then fall back to email.
        // This allows users to log in with either their username OR their email address.
        User user = userRepository.findByUserName(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() ->
                        new UsernameNotFoundException("No account found for: " + usernameOrEmail));

        // Block Google-first users from using the password login form.
        // They were created via OAuth and have no real password ("OAUTH2_NO_PASSWORD" placeholder).
        // Users who signed up with email+password first and LATER linked Google keep their
        // original provider (null / "local"), so they are NOT affected by this check —
        // they can still log in with their own password AND with Google.
        if ("google".equals(user.getProvider())) {
            throw new UsernameNotFoundException(
                    "This account was created with Google Sign-In. Please use 'Sign in with Google' to access your account.");
        }

        return UserDetailsImpl.build(user);
    }
}

