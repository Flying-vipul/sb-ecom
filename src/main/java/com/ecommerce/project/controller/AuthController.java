package com.ecommerce.project.controller;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.RefreshToken;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.OtpVerificationRequest;
import com.ecommerce.project.repositories.RoleRepository;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController  {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    AuthService authService;

    @Autowired
    EmailService emailService;

    @Autowired
    FileService fileService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    TokenBlacklistService tokenBlacklistService;

    @Autowired
    AuthRateLimiterService authRateLimiterService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticationUser(
            @RequestBody LoginRequest loginRequest,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // ── RATE LIMIT CHECK ────────────────────────────────────────────────
        // Extract real client IP (handles reverse proxies like Render/Nginx)
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = httpRequest.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim(); // take first IP if chain
        }

        if (authRateLimiterService.isBlocked(clientIp)) {
            long waitSecs = authRateLimiterService.getBlockRemainingSeconds(clientIp);
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Too many failed attempts. Try again in " + (waitSecs / 60) + " minutes.");
            map.put("Status", false);
            return new ResponseEntity<>(map, HttpStatus.TOO_MANY_REQUESTS); // 429
        }
        // ────────────────────────────────────────────────────────────────────

        // ZAPPIT SECURITY BLOCK: Check if user verified their email before letting them log in
        User user = userRepository.findByUserName(loginRequest.getUsername()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(loginRequest.getUsername()).orElse(null);
        }
        if (user != null && !user.isVerified()) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Error: Please verify your email with the OTP before logging in.");
            map.put("unverifiedEmail", user.getEmail());
            map.put("Status", false);
            return new ResponseEntity<Object>(map, HttpStatus.BAD_REQUEST);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
            //  Login succeeded — clear failed attempt counter for this IP
            authRateLimiterService.clearAttempts(clientIp);

        } catch (AuthenticationException exception) {
            //  Login failed — record failed attempt for this IP
            authRateLimiterService.recordFailedAttempt(clientIp);

            Map<String, Object> map = new HashMap<>();
            map.put("message", "Bad Credentials");
            map.put("Status", false);
            return new ResponseEntity<Object>(map, HttpStatus.UNAUTHORIZED);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Generate short-lived access token (15 min) as HttpOnly cookie
        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        // Generate long-lived refresh token (7 days) — stored in DB + HttpOnly cookie
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());
        ResponseCookie refreshCookie = jwtUtils.generateRefreshTokenCookie(refreshToken.getToken());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList();

        String profileImage = user != null ? user.getProfileImage() : null;
        UserInfoResponse response = new UserInfoResponse(
                userDetails.getId(),
                jwtCookie.getValue(),
                userDetails.getUsername(),
                roles,
                profileImage
        );

        // Access token + Refresh token BOTH in HttpOnly cookies (not localStorage)
        // Body only contains user info for Redux store
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }


    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        User existingByUsername = userRepository.findByUserName(signupRequest.getUsername()).orElse(null);
        if (existingByUsername != null && existingByUsername.isVerified()) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error : Username is already taken!"));
        }

        User existingByEmail = userRepository.findByEmail(signupRequest.getEmail()).orElse(null);
        if (existingByEmail != null && existingByEmail.isVerified()) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error : Email is already taken!"));
        }

        User user = existingByEmail != null ? existingByEmail : (existingByUsername != null ? existingByUsername : null);
        if (user != null) {
            // Re-using existing unverified user record so they are never stuck if they closed browser without OTP!
            user.setUserName(signupRequest.getUsername());
            user.setEmail(signupRequest.getEmail());
            user.setPassword(encoder.encode(signupRequest.getPassword()));
            if (existingByEmail != null && existingByUsername != null && !existingByEmail.getUserId().equals(existingByUsername.getUserId())) {
                userRepository.delete(existingByUsername);
            }
        } else {
            user = new User(
                    signupRequest.getUsername(),
                    signupRequest.getEmail(),
                    encoder.encode(signupRequest.getPassword())
            );
        }

        Set<String> strRoles = signupRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException(("Error : Role is Not Found")));
            roles.add(userRole);
        } else {
            // If user sends admin then role is ROLE_ADMIN.
            // If user sends seller then role is ROLE_SELLER.
            // If user sends normal user then role is ROLE_USER.
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin" :
                        Role adminRole = roleRepository.findByRoleName(AppRole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException(("Error : Role is Not Found")));
                        roles.add(adminRole);

                        break;
                    case "seller" :
                        Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER)
                                .orElseThrow(() -> new RuntimeException(("Error : Role is Not Found")));
                        roles.add(sellerRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException(("Error : Role is Not Found")));
                        roles.add(userRole);
                }
            });
        }

        user.setRoles(roles);
        userRepository.save(user);

        // 2. TRIGGER THE ZAPPIT OTP ENGINE
        try {
            String generatedOtp = authService.generateAndSetOtp(user.getEmail());
            emailService.sendOtpEmail(user.getEmail(), generatedOtp);
        } catch (Exception e) {
            // User is registered in DB. Email failed — tell them to use Resend OTP.
            return ResponseEntity.status(500)
                    .body(new MessageResponse("Account created, but email delivery failed. Please use \"Resend Verification Code\" on the OTP page. (" + e.getMessage() + ")"));
        }

        // 3. Tell React to show the OTP entry screen
        return ResponseEntity.ok(new MessageResponse("User registered Successfully! Please check your email for the 6-digit verification code."));
    }

    // ==========================================
    // NEW ENDPOINT: REACT CALLS THIS TO VERIFY
    // ==========================================
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        try {
            boolean isVerified = authService.verifyOtp(request.getEmail(), request.getOtp());
            if (isVerified) {
                return ResponseEntity.ok(new MessageResponse("Account successfully verified! You can now log in."));
            }
            return ResponseEntity.badRequest().body(new MessageResponse("Verification failed."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is required."));
        }
        // --- Business logic checks (400) ---
        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: No account found with this email."));
        }
        if (user.isVerified()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: This account is already verified. Please log in."));
        }
        // --- OTP generation + email send (500 if it fails) ---
        try {
            String generatedOtp = authService.generateAndSetOtp(email.trim());
            emailService.sendOtpEmail(email.trim(), generatedOtp);
            return ResponseEntity.ok(new MessageResponse("A new verification code has been sent to your email!"));
        } catch (RuntimeException e) {
            // Lockout from AuthService = client error (400)
            if (e.getMessage() != null && e.getMessage().contains("locked")) {
                return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
            }
            // SMTP / infrastructure failure = server error (500)
            return ResponseEntity.status(500)
                    .body(new MessageResponse("OTP was generated but email delivery failed. Please try again in a moment. (" + e.getMessage() + ")"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new MessageResponse("Unexpected error: " + e.getMessage()));
        }
    }

    // ... (Your existing /username, /user, and /signout methods stay exactly the same down here) ...

    @GetMapping("/username")
    public String currentUserName(Authentication authentication) {
        if (authentication != null) {
            return authentication.getName();
        }else {
            return "";
        }
    }

    @GetMapping("/user")
    public ResponseEntity<UserInfoResponse> getUserDetails(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        List<String> roles =userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList();
                
        User userRecord = userRepository.findByUserName(userDetails.getUsername()).orElse(null);
        String profileImage = userRecord != null ? userRecord.getProfileImage() : null;
        
        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),userDetails.getUsername(),roles, profileImage);

        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/signout")
    public ResponseEntity<?> signOutUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 1. Blacklist the current access token in Redis so it's immediately invalid
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                long remainingMs = jwtUtils.getExpirationFromJwt(jwt).getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    tokenBlacklistService.blacklistToken(jwt, remainingMs);
                }
            } catch (Exception e) {
                // Token may already be expired — safe to ignore
            }
        }

        // 2. Delete refresh token from DB for this user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                refreshTokenService.deleteByUsername(authentication.getName());
            } catch (Exception e) {
                // User may not have a refresh token — safe to ignore
            }
        }

        // 3. Clear BOTH cookies (access token + refresh token)
        ResponseCookie accessCookie  = jwtUtils.getCleanJwtCookie();
        ResponseCookie refreshCookie = jwtUtils.getCleanRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new MessageResponse("You've been signed out!"));
    }

    /**
     * Refresh Token Endpoint — WITH TOKEN ROTATION
     * React calls this when the access token expires (every 15 min).
     *
     * ROTATION: The old refresh token is deleted and a NEW one is issued.
     * This means a stolen refresh token can only be used ONCE — after that
     * it's gone from the DB and permanently invalid.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = "ecom-refresh-token", required = false) String cookieRefreshToken,
            @RequestBody(required = false) Map<String, String> body) {

        // Accept refresh token from either HttpOnly cookie OR request body (fallback)
        String requestRefreshToken = cookieRefreshToken;
        if (requestRefreshToken == null && body != null) {
            requestRefreshToken = body.get("refreshToken");
        }

        if (requestRefreshToken == null || requestRefreshToken.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Error: No refresh token provided. Please log in again."));
        }

        final String finalToken = requestRefreshToken;

        return refreshTokenService.findByToken(finalToken)
                .map(refreshTokenService::verifyExpiry)     // check not expired
                .map(oldToken -> {
                    User user = oldToken.getUser();

                    // ── TOKEN ROTATION ──────────────────────────────────────
                    // Delete the old refresh token — it can never be used again
                    // Create a  refresh token for this user
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getUserName());

                    // Issue a new access token WITH embedded claims (zero DB on next requests)
                    UserDetailsImpl userDetails = org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication() != null
                            ? (UserDetailsImpl) org.springframework.security.core.context.SecurityContextHolder
                                    .getContext().getAuthentication().getPrincipal()
                            : null;

                    String newAccessToken = jwtUtils.generateTokenFromUsername(user.getUserName());
                    ResponseCookie newRefreshCookie = jwtUtils.generateRefreshTokenCookie(newRefreshToken.getToken());

                    Map<String, String> responseMap = new HashMap<>();
                    responseMap.put("accessToken", newAccessToken);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                            .body((Object) responseMap);
                })
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Error: Refresh token not found. Please log in again.")));
    }

    @PutMapping("/profile/image")
    public ResponseEntity<UserInfoResponse> uploadProfileImage(@RequestParam("image") MultipartFile image) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByUserName(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            String imageUrl = fileService.uploadImage("profile", image);
            user.setProfileImage(imageUrl);
            userRepository.save(user);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .toList();
            
            UserInfoResponse response = new UserInfoResponse(userDetails.getId(), userDetails.getUsername(), roles, imageUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Image upload failed", e);
        }
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is required."));
        }
        try {
            String otp = authService.generatePasswordResetOtp(email.trim());
            emailService.sendPasswordResetOtpEmail(email.trim(), otp);
            return ResponseEntity.ok(new MessageResponse("Password reset code sent! Check your email inbox."));
        } catch (RuntimeException e) {
            // Business logic error (user not found, locked) → 400
            if (e.getMessage() != null && (e.getMessage().contains("No account") || e.getMessage().contains("locked"))) {
                return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
            }
            // SMTP failure → 500
            return ResponseEntity.status(500)
                    .body(new MessageResponse("Reset code was generated but email delivery failed. Please try again. (" + e.getMessage() + ")"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        String newPassword = request.get("newPassword");

        if (email == null || otp == null || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email, OTP, and a new password (min 6 chars) are required."));
        }
        try {
            authService.resetPassword(email.trim(), otp.trim(), newPassword, encoder);
            return ResponseEntity.ok(new MessageResponse("Password reset successful! You can now log in with your new password."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
