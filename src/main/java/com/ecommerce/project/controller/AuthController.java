package com.ecommerce.project.controller;

import com.ecommerce.project.payload.OtpVerificationRequest;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.AuthLogoutResponse;
import com.ecommerce.project.security.response.AuthResponse;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.security.response.TokenRefreshResponse;
import com.ecommerce.project.security.response.UserInfoResponse;
import com.ecommerce.project.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST Controller for Authentication and Account Management endpoints.
 *
 * Follows the Single Responsibility Principle:
 * - Only handles HTTP mapping, request extraction, and response wrapping.
 * - All business logic, transaction management, and persistence reside in AuthService.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signin")
    public ResponseEntity<UserInfoResponse> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        AuthResponse response = authService.authenticateUser(loginRequest, clientIp);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, response.getJwtCookie().toString())
                .header(HttpHeaders.SET_COOKIE, response.getRefreshCookie().toString())
                .body(response.getUserInfo());
    }

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        MessageResponse response = authService.registerUser(signupRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        MessageResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@RequestBody Map<String, String> request) {
        MessageResponse response = authService.resendOtp(request.get("email"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signout")
    public ResponseEntity<MessageResponse> signOutUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        String username = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName()
                : null;

        AuthLogoutResponse response = authService.signOut(authHeader, username);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, response.getCleanJwtCookie().toString())
                .header(HttpHeaders.SET_COOKIE, response.getCleanRefreshCookie().toString())
                .body(response.getMessage());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = "ecom-refresh-token", required = false) String cookieRefreshToken,
            @RequestBody(required = false) Map<String, String> body) {

        String rawToken = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (body != null ? body.get("refreshToken") : null);

        TokenRefreshResponse response = authService.refreshToken(rawToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, response.getRefreshCookie().toString())
                .body(response.getBody());
    }

    @GetMapping("/user")
    public ResponseEntity<UserInfoResponse> getUserDetails(Authentication authentication) {
        UserInfoResponse response = authService.getCurrentUserDetails(authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/username")
    public String currentUserName(Authentication authentication) {
        return authentication != null ? authentication.getName() : "";
    }

    @PutMapping("/profile/image")
    public ResponseEntity<UserInfoResponse> uploadProfileImage(
            @RequestParam("image") MultipartFile image,
            Authentication authentication) {

        UserInfoResponse response = authService.updateProfileImage(authentication.getName(), image);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@RequestBody Map<String, String> request) {
        MessageResponse response = authService.forgotPassword(request.get("email"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@RequestBody Map<String, String> request) {
        MessageResponse response = authService.resetPassword(
                request.get("email"),
                request.get("otp"),
                request.get("newPassword")
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts client IP from reverse proxy headers (X-Forwarded-For) or fallback remote address.
     */
    private String extractClientIp(HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            return httpRequest.getRemoteAddr();
        }
        return clientIp.split(",")[0].trim();
    }
}
