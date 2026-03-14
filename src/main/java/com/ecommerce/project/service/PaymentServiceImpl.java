package com.ecommerce.project.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${razorpay.api.key}")
    private String razorpayKey;

    @Value("${razorpay.api.secret}")
    private String razorpaySecret;

    @Override
    public String createRazorpayOrder(double amount, String currency) {
        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKey, razorpaySecret);

            JSONObject orderRequest = new JSONObject();
            // Razorpay expects amount in paise (multiply by 100)
            // To this line (cast it to an integer!):
            orderRequest.put("amount", (int)(amount * 100));
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpay.orders.create(orderRequest);
            return order.toString(); // Returns the JSON string containing the 'id'

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Razorpay Order: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            // This is the anti-hacker check. It mashes the orderId and paymentId with your secret key.
            String payload = razorpayOrderId + "|" + razorpayPaymentId;

            // Razorpay's Utils class does the heavy HMAC-SHA256 cryptography for you
            return Utils.verifySignature(payload, razorpaySignature, razorpaySecret);

        } catch (Exception e) {
            System.out.println("Payment verification failed: " + e.getMessage());
            return false;
        }
    }
}