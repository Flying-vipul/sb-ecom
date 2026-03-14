package com.ecommerce.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async // PROFESSIONAL UPGRADE: Runs in the background
    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();

        // This must match the email in your application.properties
        message.setFrom("your.zappit.email@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Your Zappit Security Code");

        message.setText("Welcome to Zappit! 🚀\n\n"
                + "Your 6-digit verification code is: " + otp + "\n\n"
                + "This code will expire in exactly 5 minutes.\n"
                + "If you did not request this, please ignore this email.\n\n"
                + "- The Zappit Team");

        mailSender.send(message);
    }
}