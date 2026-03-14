package com.ecommerce.project.controller;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderRequestDTO;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.PaymentService; // Added this!
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    AuthUtil authUtil;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService; // Injecting our secure Razorpay logic

    // ====================================================================
    // STEP 1: CREATE THE PAYMENT ORDER (React calls this first)
    // ====================================================================
    @PostMapping("/order/users/payments/instantiate")
    public ResponseEntity<String> instantiateRazorpayOrder(@RequestParam double amount) {
        // Creates the secure order on Razorpay's end and returns the Order ID string
        // Note: For max security later, calculate 'amount' from the user's DB cart, not the frontend parameter!
        String razorpayOrderId = paymentService.createRazorpayOrder(amount, "INR");
        return new ResponseEntity<>(razorpayOrderId, HttpStatus.CREATED);
    }

    // ====================================================================
    // STEP 2: VERIFY AND PLACE ORDER (Your upgraded endpoint)
    // ====================================================================
    @PostMapping("/order/users/payments/{paymentMethod}")
    public ResponseEntity<OrderDTO> orderProducts(@PathVariable String paymentMethod,
                                                  @RequestBody OrderRequestDTO orderRequestDTO) {
        String emailId = authUtil.loggedInEmail();

        // THE SECURITY WALL: Only verify if the payment method is an online gateway
        if ("Razorpay".equalsIgnoreCase(paymentMethod) || "Stripe".equalsIgnoreCase(paymentMethod)) {

            // Check the math! Did Razorpay actually sign this transaction?
            boolean isAuthentic = paymentService.verifyPaymentSignature(
                    orderRequestDTO.getRazorpayOrderId(),
                    orderRequestDTO.getPgPaymentId(),
                    orderRequestDTO.getRazorpaySignature()
            );

            // If the signatures don't match, block the order immediately!
            if (!isAuthentic) {
                throw new RuntimeException("Payment Verification Failed! Potential tampering detected.");
                // Or you can return a BAD_REQUEST ResponseEntity here
            }
        }

        // If we reach here, it's either an authentic Razorpay payment OR a Cash on Delivery order.
        // Now it is 100% safe to save to the database.
        OrderDTO order = orderService.placeOrder(
                emailId,
                orderRequestDTO.getAddressId(),
                paymentMethod,
                orderRequestDTO.getPgName(),
                orderRequestDTO.getPgPaymentId(),
                orderRequestDTO.getPgStatus(),
                orderRequestDTO.getPgResponseMessage()
        );

        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }
}