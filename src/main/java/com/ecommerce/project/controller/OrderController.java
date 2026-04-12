package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderRequestDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderStatusUpdateDTO;
import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private OrderService orderService;

    // STEP 1: CREATE THE PAYMENT ORDER
    @PostMapping("/order/users/payments/instantiate")
    public ResponseEntity<String> instantiateRazorpayOrder() {
        // Get email securely from token. The backend calculates the amount, NOT the frontend!
        String emailId = authUtil.loggedInEmail();
        String razorpayOrderId = orderService.createRazorpayOrder(emailId);
        return new ResponseEntity<>(razorpayOrderId, HttpStatus.CREATED);
    }

    // STEP 2: VERIFY AND PLACE ORDER
    @PostMapping("/order/users/payments/{paymentMethod}")
    public ResponseEntity<OrderDTO> orderProducts(@PathVariable String paymentMethod,
                                                  @RequestBody OrderRequestDTO orderRequestDTO) {
        String emailId = authUtil.loggedInEmail();

        // Pass everything to the service layer. It will handle the signature verification securely.
        OrderDTO order = orderService.placeOrder(
                emailId,
                orderRequestDTO.getAddressId(),
                paymentMethod,
                orderRequestDTO.getPgName(),
                orderRequestDTO.getPgPaymentId(),
                orderRequestDTO.getPgStatus(),
                orderRequestDTO.getPgResponseMessage(),
                orderRequestDTO.getRazorpayOrderId(),
                orderRequestDTO.getRazorpaySignature()
        );

        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<OrderResponse> getAllOrders(
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER , required = false) Integer pageNumber,
            @RequestParam(name = "pageSize" , defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false ) String sortBy,
            @RequestParam(name = "sortOrder" ,defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        OrderResponse orderResponse = orderService.getAllOrders(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse,HttpStatus.OK);
    }

    @PutMapping("/admin/orders/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDTO orderStatusUpdateDTO ){
        OrderDTO order =  orderService.updateOrder(orderId, orderStatusUpdateDTO.getStatus());
        return new ResponseEntity<OrderDTO>(order, HttpStatus.OK);
    }


    @GetMapping("/seller/orders")
    public ResponseEntity<OrderResponse> getAllSellerOrders(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        OrderResponse orderResponse = orderService.getAllSellerOrders(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse, HttpStatus.OK);
    }

    @GetMapping("/user/orders")
    public ResponseEntity<java.util.List<OrderDTO>> getUserOrders() {
        String emailId = authUtil.loggedInEmail();
        java.util.List<OrderDTO> orders = orderService.getUserOrders(emailId);
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    
}