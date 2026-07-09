package com.ecommerce.project.security;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.RoleRepository;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Called by Spring Security after Google OAuth2 succeeds.
 * Flow: Google → Spring Backend → this handler → redirect to React frontend with JWT.
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oAuth2User.getAttributes();

            String email   = (String) attributes.get("email");
            String picture = (String) attributes.get("picture");

            logger.info("OAuth2 login success for email: {}", email);

            // --- Find or create the user ---
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                // New Google user — auto-create their account
                String baseUsername = email.split("@")[0]
                        .replaceAll("[^a-zA-Z0-9_]", "")
                        .toLowerCase();
                if (baseUsername.length() > 18) baseUsername = baseUsername.substring(0, 18);

                String username = baseUsername;
                int suffix = 1;
                while (userRepository.existsByUserName(username)) {
                    username = baseUsername + suffix++;
                }

                Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                        .orElseThrow(() -> new RuntimeException("Default ROLE_USER not found in DB"));

                User newUser = new User();
                newUser.setUserName(username);
                newUser.setEmail(email);
                // OAuth users have no local password — store a placeholder to satisfy any DB constraints
                newUser.setPassword("OAUTH2_NO_PASSWORD");
                newUser.setProvider("google");
                newUser.setProfileImage(picture);
                newUser.setVerified(true);
                newUser.setRoles(Set.of(userRole));

                logger.info("Creating new OAuth2 user: username={}, email={}", username, email);
                return userRepository.save(newUser);
            });

            // If existing local user signs in with Google, link their account
            if (!"google".equals(user.getProvider())) {
                user.setProvider("google");
                if (user.getProfileImage() == null && picture != null) {
                    user.setProfileImage(picture);
                }
                user.setVerified(true);
                userRepository.save(user);
                logger.info("Linked existing user {} to Google OAuth2", user.getUserName());
            }

            // --- Generate JWT ---
            String jwt = jwtUtils.generateTokenFromUsername(user.getUserName());

            // --- Build roles string ---
            String roles = user.getRoles().stream()
                    .map(r -> r.getRoleName().name())
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);

            // --- Redirect to React frontend ---
            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/callback")
                    .queryParam("token", jwt)
                    .queryParam("username", user.getUserName())
                    .queryParam("id", user.getUserId())
                    .queryParam("roles", roles)
                    .queryParam("profileImage", user.getProfileImage() != null ? user.getProfileImage() : "")
                    .build().toUriString();

            logger.info("Redirecting OAuth2 user {} to frontend: {}", user.getUserName(), frontendUrl + "/oauth2/callback");
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            // Any failure → redirect to login page with an error param instead of showing Spring's 500 page
            logger.error("OAuth2 authentication success handler failed: {}", e.getMessage(), e);
            String errorUrl = frontendUrl + "/login?error=oauth_failed";
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }
}
