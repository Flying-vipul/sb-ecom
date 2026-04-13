package com.ecommerce.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    // Dynamically pulls your email from application.properties
    @Value("${spring.mail.username}")
    private String senderEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(senderEmail);
            message.setTo(toEmail);
            message.setSubject("Your Zappit Security Code");

            message.setText("Welcome to Zappit! 🚀\n\n"
                    + "Your 6-digit verification code is: " + otp + "\n\n"
                    + "This code will expire in exactly 5 minutes.\n"
                    + "If you did not request this, please ignore this email.\n\n"
                    + "- The Zappit Team");

            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendOrderStatusUpdateEmail(String toEmail, Long orderId, String newStatus) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(senderEmail);
            message.setTo(toEmail);
            message.setSubject("Zappit Order Update: Order #" + orderId);

            message.setText("Hello! 📦\n\n"
                    + "Great news! The status of your Zappit order #" + orderId + " has been updated to: " + newStatus + "\n\n"
                    + "You can track your order directly from your 'My Orders' dashboard on Zappit.\n\n"
                    + "- The Zappit Team");

            mailSender.send(message);
            logger.info("Order update email sent successfully for Order ID: {} to {}", orderId, toEmail);
        } catch (Exception e) {
            logger.error("Failed to send order update email for Order ID {} to {}: {}", orderId, toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendContactInquiryEmail(com.ecommerce.project.payload.ContactRequest request) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            // Uses the injected email for both sender and recipient to prevent SMTP spoofing blocks
            message.setFrom(senderEmail);
            message.setTo(senderEmail);
            message.setSubject("New Zappit Contact Inquiry from: " + request.getName());

            message.setText("You have a new contact inquiry via Zappit India!\n\n"
                    + "User Name: " + request.getName() + "\n"
                    + "User Email: " + request.getEmail() + "\n\n"
                    + "Message:\n" + request.getMessage() + "\n\n"
                    + "-----------------------------------\n"
                    + "Reply directly to: " + request.getEmail() + "\n"
                    + "The Zappit Official Robot 🤖");

            mailSender.send(message);
            logger.info("Contact inquiry email sent successfully from: {}", request.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send contact inquiry email from {}: {}", request.getEmail(), e.getMessage(), e);
        }
    }
}