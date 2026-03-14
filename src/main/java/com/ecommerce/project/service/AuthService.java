package com.ecommerce.project.service;

import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    // PROFESSIONAL UPGRADE 1: Cryptographically Secure Random Number Generator
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 6-digit OTP, saves it to the user with a 5-minute expiry,
     * and resets any previous security counters.
     */
    @Transactional // PROFESSIONAL UPGRADE 2: Ensures DB consistency
    public String generateAndSetOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // DEFENSE LAYER 1: Stop hackers from spamming "Resend OTP" if they are locked out
        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            long minutesLeft = Duration.between(LocalDateTime.now(), user.getAccountLockedUntil()).toMinutes();
            throw new RuntimeException("Account is locked due to multiple failed attempts. Try again in " + minutesLeft + " minutes.");
        }

        // Generate a true, unguessable 6-digit number (between 100000 and 999999)
        int otpNum = 100000 + secureRandom.nextInt(900000);
        String generatedOtp = String.valueOf(otpNum);

        // Arm the Ticking Time Bomb (5 Minutes)
        user.setOtp(generatedOtp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));

        // Clean the slate (reset attempts and unlock)
        user.setOtpAttempts(0);
        user.setAccountLockedUntil(null);

        userRepository.save(user);

        return generatedOtp;
        // Note: You will return this string to your Controller, which will pass it to the EmailService!
    }

    /**
     * Verifies the OTP entered by the user. Handles expiry, max attempts, and lockouts.
     */
    @Transactional
    public boolean verifyOtp(String email, String enteredOtp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // DEFENSE LAYER 2: Is the account currently locked?
        if (user.getAccountLockedUntil() != null && LocalDateTime.now().isBefore(user.getAccountLockedUntil())) {
            throw new RuntimeException("Account is locked. Please wait until the cooldown period ends.");
        }

        // DEFENSE LAYER 3: Was an OTP even requested? Or has it expired?
        if (user.getOtp() == null || user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP is missing or expired. Please request a new one.");
        }

        // THE MOMENT OF TRUTH: Does the OTP match?
        if (user.getOtp().equals(enteredOtp)) {
            // SUCCESS! Disarm the bomb and officially verify the user
            user.setVerified(true);
            user.setOtp(null); // Destroy the OTP
            user.setOtpExpiry(null);
            user.setOtpAttempts(0);
            user.setAccountLockedUntil(null);

            userRepository.save(user);
            return true;
        } else {
            // FAILURE! Trigger the self-destruct sequence
            int currentAttempts = user.getOtpAttempts() + 1;
            user.setOtpAttempts(currentAttempts);

            if (currentAttempts >= 3) {
                // Maximum attempts reached. Lock the account for 15 minutes.
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.setOtp(null); // Destroy the current OTP so a bot can't guess it later
                user.setOtpExpiry(null);
                userRepository.save(user);

                throw new RuntimeException("Maximum attempts reached. Account locked for 15 minutes for your security.");
            }

            // Save the incremented attempt counter and warn the user
            userRepository.save(user);
            throw new RuntimeException("Invalid OTP. You have " + (3 - currentAttempts) + " attempt(s) left.");
        }
    }
}