package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ContactRequest;
import com.ecommerce.project.security.response.MessageResponse;
import com.ecommerce.project.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class ContactController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/contact")
    public ResponseEntity<?> handleContactInquiry(@Valid @RequestBody ContactRequest request) {
        try {
            // Instantly returns 200 OK because email happens @Async asynchronously!
            emailService.sendContactInquiryEmail(request);
            return ResponseEntity.ok(new MessageResponse("Message sent successfully. We will get back to you soon."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Failed to send message: " + e.getMessage()));
        }
    }
}
