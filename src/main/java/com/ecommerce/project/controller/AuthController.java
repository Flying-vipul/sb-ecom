package com.ecommerce.project.controller;

import com.ecommerce.project.model.AppRole;
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
import com.ecommerce.project.service.UserDetailsImpl;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.service.EmailService;
import com.ecommerce.project.service.FileService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
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

    // INJECT THE NEW ZAPPIT ENGINES HERE
    @Autowired
    AuthService authService;

    @Autowired
    EmailService emailService;

    @Autowired
    FileService fileService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticationUser(@RequestBody LoginRequest loginRequest) {

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
        try{
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
        }catch (AuthenticationException exception) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Bad Credentials");
            map.put("Status" ,false);

            return new ResponseEntity<Object>(map, HttpStatus.UNAUTHORIZED);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        List<String> roles =userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList();
                
        String profileImage = user != null ? user.getProfileImage() : null;
        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),jwtCookie.getValue(), userDetails.getUsername(),roles, profileImage);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                jwtCookie.toString())
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
    public ResponseEntity<?> signOutUser() {
        ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                        cookie.toString())
                .body(new MessageResponse("You've been signed out!"));
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
