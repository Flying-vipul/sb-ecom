package com.ecommerce.project.service;

import com.ecommerce.project.payload.OtpVerificationRequest;
import com.ecommerce.project.security.request.LoginRequest;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.security.response.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    AuthResponse authenticateUser(LoginRequest loginRequest, String clientIp);

    MessageResponse registerUser(SignupRequest signupRequest);

    MessageResponse verifyOtp(OtpVerificationRequest request);

    MessageResponse resendOtp(String email);

    AuthLogoutResponse signOut(String authHeader, String username);

    TokenRefreshResponse refreshToken(String rawRefreshToken);

    UserInfoResponse updateProfileImage(String username, MultipartFile image);

    MessageResponse forgotPassword(String email);

    MessageResponse resetPassword(String email, String otp, String newPassword);

    UserInfoResponse getCurrentUserDetails(Authentication authentication);

    String generateAndSetOtp(String email);

    boolean verifyOtp(String email, String enteredOtp);

    String generatePasswordResetOtp(String email);

    void resetPassword(String email, String enteredOtp, String newPassword, PasswordEncoder encoder);
}
