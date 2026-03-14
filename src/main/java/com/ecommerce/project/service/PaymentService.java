package com.ecommerce.project.service;

public interface PaymentService {

    // 1. Creates the secure order on Razorpay's servers
    String createRazorpayOrder(double amount, String currency);

    // 2. The Cryptographic vault: Verifies the hacker didn't spoof the payment
    boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature);
}
