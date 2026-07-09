package com.ecommerce.project.service;

import com.ecommerce.project.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Email service using Brevo's Transactional Email HTTP API.
 * NO SMTP — uses REST API with just an API key. Works on Render, Railway, Azure, etc.
 *
 * API docs: https://developers.brevo.com/reference/sendtransacemail
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SENDER_NAME = "Zappit India";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${spring.mail.from:zappit.india@gmail.com}")
    private String senderEmail;

    // ==========================================
    // SHARED HELPER: Build and send via Brevo API
    // ==========================================
    private void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
        try {
            // Build request body per Brevo API spec
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sender", Map.of("name", SENDER_NAME, "email", senderEmail));
            body.put("to", List.of(Map.of("email", toEmail)));
            body.put("subject", subject);
            body.put("htmlContent", htmlBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Email sent via Brevo API to: {} | Subject: {}", toEmail, subject);
            } else {
                logger.error("Brevo API returned status {} for email to {}", response.getStatusCode(), toEmail);
                throw new RuntimeException("Brevo API returned: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            logger.error("Brevo API error sending to {}: {} — {}", toEmail, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send email via Brevo: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Failed to send email to {} | Subject: {} | Error: {}", toEmail, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    // ==========================================
    // 1. OTP VERIFICATION EMAIL (Registration)
    // ==========================================
    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "Your Zappit Verification Code: " + otp;
        String htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
            + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>"
            + "<tr><td style='background:linear-gradient(135deg,#6366f1 0%,#7c3aed 100%);padding:36px 40px;text-align:center'>"
            + "<h1 style='color:#fff;margin:0;font-size:28px;font-weight:800;letter-spacing:-0.5px'>Zappit India &#x1F381;</h1>"
            + "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px'>Toys, Gifts &amp; Showpieces</p>"
            + "</td></tr>"
            + "<tr><td style='padding:40px'>"
            + "<h2 style='color:#1e293b;margin:0 0 8px;font-size:22px;font-weight:700'>Verify Your Email Address</h2>"
            + "<p style='color:#64748b;font-size:15px;line-height:1.6;margin:0 0 28px'>Welcome to Zappit! Use the code below to verify your account. This code expires in <strong>5 minutes</strong>.</p>"
            + "<div style='background:#f8fafc;border:2px dashed #6366f1;border-radius:12px;padding:28px;text-align:center;margin:0 0 28px'>"
            + "<p style='color:#64748b;font-size:13px;margin:0 0 8px;text-transform:uppercase;letter-spacing:1.5px;font-weight:600'>Your Verification Code</p>"
            + "<p style='color:#6366f1;font-size:44px;font-weight:900;letter-spacing:10px;margin:0;font-family:monospace'>" + otp + "</p>"
            + "</div>"
            + "<p style='color:#94a3b8;font-size:13px;margin:0;line-height:1.6'>If you did not create a Zappit account, please ignore this email.</p>"
            + "</td></tr>"
            + "<tr><td style='background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
            + "<p style='color:#94a3b8;font-size:12px;margin:0'>&copy; 2024 Zappit India. All rights reserved.</p>"
            + "</td></tr></table></td></tr></table></body></html>";

        sendHtmlEmail(toEmail, subject, htmlBody);
    }

    // ==========================================
    // 2. PASSWORD RESET OTP EMAIL
    // ==========================================
    public void sendPasswordResetOtpEmail(String toEmail, String otp) {
        String subject = "Zappit Password Reset Code: " + otp;
        String htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
            + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>"
            + "<tr><td style='background:linear-gradient(135deg,#dc2626 0%,#9f1239 100%);padding:36px 40px;text-align:center'>"
            + "<h1 style='color:#fff;margin:0;font-size:28px;font-weight:800;letter-spacing:-0.5px'>Zappit India &#x1F512;</h1>"
            + "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px'>Account Security</p>"
            + "</td></tr>"
            + "<tr><td style='padding:40px'>"
            + "<h2 style='color:#1e293b;margin:0 0 8px;font-size:22px;font-weight:700'>Password Reset Request</h2>"
            + "<p style='color:#64748b;font-size:15px;line-height:1.6;margin:0 0 28px'>We received a request to reset the password for your Zappit account. Use the code below. This code expires in <strong>5 minutes</strong>.</p>"
            + "<div style='background:#fff5f5;border:2px dashed #dc2626;border-radius:12px;padding:28px;text-align:center;margin:0 0 28px'>"
            + "<p style='color:#64748b;font-size:13px;margin:0 0 8px;text-transform:uppercase;letter-spacing:1.5px;font-weight:600'>Password Reset Code</p>"
            + "<p style='color:#dc2626;font-size:44px;font-weight:900;letter-spacing:10px;margin:0;font-family:monospace'>" + otp + "</p>"
            + "</div>"
            + "<p style='color:#94a3b8;font-size:13px;margin:0;line-height:1.6'><strong>Did not request this?</strong> Your password will remain unchanged. If you are concerned, please contact our support team immediately.</p>"
            + "</td></tr>"
            + "<tr><td style='background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
            + "<p style='color:#94a3b8;font-size:12px;margin:0'>&copy; 2024 Zappit India. All rights reserved.</p>"
            + "</td></tr></table></td></tr></table></body></html>";

        sendHtmlEmail(toEmail, subject, htmlBody);
    }

    // ==========================================
    // 3. ORDER STATUS UPDATE EMAIL
    // ==========================================
    @Async
    public void sendOrderStatusUpdateEmail(String toEmail, Long orderId, String newStatus) {
        try {
            String subject;
            String htmlBody;

            if ("Delivered".equalsIgnoreCase(newStatus)) {
                subject = "\uD83C\uDF89 Your Zappit Order #" + orderId + " Has Been Delivered!";
                htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif'>"
                    + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
                    + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>"
                    + "<tr><td style='background:linear-gradient(135deg,#059669 0%,#0f766e 100%);padding:36px 40px;text-align:center'>"
                    + "<h1 style='color:#fff;margin:0;font-size:32px;font-weight:800'>&#x1F389; Delivered!</h1>"
                    + "<p style='color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:15px'>Your Zappit order has arrived</p>"
                    + "</td></tr><tr><td style='padding:40px'>"
                    + "<h2 style='color:#1e293b;margin:0 0 8px;font-size:22px;font-weight:700'>Your order is at your doorstep! &#x1F4E6;</h2>"
                    + "<p style='color:#64748b;font-size:15px;line-height:1.6;margin:0 0 24px'>Great news! Your Zappit order <strong>#" + orderId + "</strong> has been successfully delivered.</p>"
                    + "<div style='background:#f0fdf4;border:1px solid #86efac;border-radius:12px;padding:20px;margin:0 0 24px'>"
                    + "<p style='color:#166534;font-size:14px;font-weight:700;margin:0 0 4px;text-transform:uppercase;letter-spacing:1px'>Order Status</p>"
                    + "<p style='color:#059669;font-size:26px;font-weight:900;margin:0'>&#x2713; Delivered</p>"
                    + "</div>"
                    + "<p style='color:#94a3b8;font-size:13px;margin:0'>Any issues? Contact us at <a href='mailto:" + senderEmail + "' style='color:#059669'>" + senderEmail + "</a></p>"
                    + "</td></tr><tr><td style='background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
                    + "<p style='color:#94a3b8;font-size:12px;margin:0'>&#169; 2024 Zappit India. All rights reserved.</p>"
                    + "</td></tr></table></td></tr></table></body></html>";
            } else {
                subject = "Zappit Order Update: Order #" + orderId + " is now " + newStatus;
                htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif'>"
                    + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
                    + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>"
                    + "<tr><td style='background:linear-gradient(135deg,#6366f1 0%,#7c3aed 100%);padding:36px 40px;text-align:center'>"
                    + "<h1 style='color:#fff;margin:0;font-size:28px;font-weight:800'>Zappit India &#x1F4E6;</h1>"
                    + "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px'>Order Update</p>"
                    + "</td></tr><tr><td style='padding:40px'>"
                    + "<h2 style='color:#1e293b;margin:0 0 8px;font-size:22px;font-weight:700'>Your Order Status Has Changed!</h2>"
                    + "<p style='color:#64748b;font-size:15px;margin:0 0 24px'>Order <strong>#" + orderId + "</strong> is now:</p>"
                    + "<div style='background:#f0fdf4;border-radius:12px;padding:20px;border-left:4px solid #059669'>"
                    + "<p style='color:#059669;font-size:22px;font-weight:800;margin:0'>" + newStatus + "</p>"
                    + "</div>"
                    + "<p style='color:#94a3b8;font-size:13px;margin:24px 0 0'>Track your order from <strong>My Orders</strong> on Zappit.</p>"
                    + "</td></tr><tr><td style='background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
                    + "<p style='color:#94a3b8;font-size:12px;margin:0'>&#169; 2024 Zappit India. All rights reserved.</p>"
                    + "</td></tr></table></td></tr></table></body></html>";
            }

            sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (Exception e) {
            logger.error("Failed to send order status email for Order #{} to {}: {}", orderId, toEmail, e.getMessage(), e);
        }
    }

    // ==========================================
    // 4. ORDER CONFIRMATION EMAIL (on order placed)
    // ==========================================
    @Async
    public void sendOrderConfirmationEmail(String toEmail, Long orderId, Double totalAmount, List<OrderItem> items) {
        try {
            String subject = "\u2705 Order Confirmed! Your Zappit Order #" + orderId;

            StringBuilder itemRows = new StringBuilder();
            for (OrderItem item : items) {
                String productName = item.getProduct() != null ? item.getProduct().getProductName() : "Product";
                itemRows.append("<tr>")
                    .append("<td style='padding:10px 0;border-bottom:1px solid #f1f5f9;color:#1e293b;font-size:14px'>").append(productName).append("</td>")
                    .append("<td style='padding:10px 0;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:14px;text-align:center'>").append(item.getQuantity()).append("</td>")
                    .append("<td style='padding:10px 0;border-bottom:1px solid #f1f5f9;color:#6366f1;font-size:14px;text-align:right;font-weight:600'>&#8377;").append(String.format("%.2f", item.getOrderedProductPrice())).append("</td>")
                    .append("</tr>");
            }

            String htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif'>"
                + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
                + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>"
                + "<tr><td style='background:linear-gradient(135deg,#6366f1 0%,#7c3aed 100%);padding:36px 40px;text-align:center'>"
                + "<h1 style='color:#fff;margin:0;font-size:28px;font-weight:800'>Order Confirmed! &#x2705;</h1>"
                + "<p style='color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:15px'>Thank you for shopping at Zappit India</p>"
                + "</td></tr><tr><td style='padding:40px'>"
                + "<h2 style='color:#1e293b;margin:0 0 4px;font-size:20px;font-weight:700'>Your order is confirmed &#x1F389;</h2>"
                + "<p style='color:#64748b;font-size:14px;margin:0 0 28px'>We've received your order and are processing it right away.</p>"
                + "<div style='background:#f8fafc;border-radius:10px;padding:16px 20px;margin:0 0 24px'>"
                + "<span style='color:#64748b;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:1px'>Order ID &nbsp;</span>"
                + "<span style='color:#6366f1;font-size:15px;font-weight:800'>#" + orderId + "</span>"
                + "</div>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='margin:0 0 20px'>"
                + "<tr><th style='padding:10px 0;text-align:left;font-size:12px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px'>Item</th>"
                + "<th style='padding:10px 0;text-align:center;font-size:12px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px'>Qty</th>"
                + "<th style='padding:10px 0;text-align:right;font-size:12px;color:#94a3b8;text-transform:uppercase;letter-spacing:1px'>Price</th></tr>"
                + itemRows
                + "</table>"
                + "<div style='background:linear-gradient(135deg,#6366f1,#7c3aed);border-radius:10px;padding:16px 20px;text-align:right;margin:0 0 24px'>"
                + "<span style='color:rgba(255,255,255,0.8);font-size:13px;font-weight:600'>Total Paid &nbsp;</span>"
                + "<span style='color:#fff;font-size:20px;font-weight:900'>&#8377;" + String.format("%.2f", totalAmount) + "</span>"
                + "</div>"
                + "<p style='color:#94a3b8;font-size:13px;margin:0'>We'll notify you when your order ships. Track it in <strong>My Orders</strong> on Zappit.</p>"
                + "</td></tr><tr><td style='background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
                + "<p style='color:#94a3b8;font-size:12px;margin:0'>&#169; 2024 Zappit India. All rights reserved. &nbsp;|&nbsp; <a href='mailto:" + senderEmail + "' style='color:#6366f1;text-decoration:none'>" + senderEmail + "</a></p>"
                + "</td></tr></table></td></tr></table></body></html>";

            sendHtmlEmail(toEmail, subject, htmlBody);
            logger.info("Order confirmation email sent for Order #{} to: {}", orderId, toEmail);
        } catch (Exception e) {
            logger.error("Failed to send order confirmation email for Order #{} to {}: {}", orderId, toEmail, e.getMessage(), e);
        }
    }

    // ==========================================
    // 5. CONTACT INQUIRY EMAIL
    // ==========================================
    @Async
    public void sendContactInquiryEmail(com.ecommerce.project.payload.ContactRequest request) {
        try {
            String subject = "New Zappit Contact Inquiry from: " + request.getName();
            String htmlBody = "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif'>"
                + "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 16px'>"
                + "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden'>"
                + "<tr><td style='background:#1e293b;padding:24px 40px'>"
                + "<h1 style='color:#fff;margin:0;font-size:20px;font-weight:700'>Zappit Contact Inquiry &#x1F4AC;</h1></td></tr>"
                + "<tr><td style='padding:32px 40px'>"
                + "<p style='color:#1e293b;font-size:15px;margin:0 0 16px'><strong>From:</strong> " + request.getName() + "</p>"
                + "<p style='color:#1e293b;font-size:15px;margin:0 0 16px'><strong>Email:</strong> " + request.getEmail() + "</p>"
                + "<p style='color:#64748b;font-size:14px;margin:0 0 8px;font-weight:600'>Message:</p>"
                + "<div style='background:#f8fafc;border-left:3px solid #6366f1;padding:16px;border-radius:0 8px 8px 0'>"
                + "<p style='color:#1e293b;font-size:14px;margin:0;line-height:1.7'>" + request.getMessage() + "</p>"
                + "</div>"
                + "</td></tr><tr><td style='background:#f8fafc;padding:16px 40px;text-align:center;border-top:1px solid #e2e8f0'>"
                + "<p style='color:#94a3b8;font-size:12px;margin:0'>Reply to: " + request.getEmail() + " | Zappit India</p>"
                + "</td></tr></table></td></tr></table></body></html>";

            sendHtmlEmail(senderEmail, subject, htmlBody);
            logger.info("Contact inquiry email sent from: {}", request.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send contact inquiry email from {}: {}", request.getEmail(), e.getMessage(), e);
        }
    }
}