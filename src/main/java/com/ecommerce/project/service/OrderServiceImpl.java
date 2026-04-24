package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.util.AuthUtil;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    CartRepository cartRepository;

    @Autowired
    AddressRepository addressRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    EmailService emailService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Autowired
    AuthUtil authUtil;

    @Autowired
    CartService cartService;


    // =========================================================================
    // STEP 1: CREATE RAZORPAY ORDER (before checkout)
    // =========================================================================
    @Override
    public String createRazorpayOrder(String emailId) {
        // 1. Get the cart — amount is calculated server-side, never trust frontend
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        try {
            // 2. Initialize Razorpay Client
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // 3. Build order request — amount MUST be in paise (₹ × 100)
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (cart.getTotalPrice() * 100));
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            // 4. Call Razorpay API and return the order ID to frontend
            com.razorpay.Order razorpayOrder = razorpay.orders.create(orderRequest);
            return razorpayOrder.get("id");

        } catch (RazorpayException e) {
            throw new APIException("Failed to create Razorpay Order: " + e.getMessage());
        }
    }


    // =========================================================================
    // STEP 2: PLACE ORDER (after payment, verify signature then persist)
    // =========================================================================
    @Transactional
    @Override
    public OrderDTO placeOrder(
            String emailId, Long addressId, String paymentMethod,
            String pgName, String razorpayPaymentId, String pgStatus,
            String pgResponseMessage, String razorpayOrderId, String razorpaySignature) {

        // 1. SECURITY: Verify Razorpay signature on the server — never trust the frontend
        if ("Razorpay".equalsIgnoreCase(paymentMethod)) {
            try {
                JSONObject options = new JSONObject();
                options.put("razorpay_order_id", razorpayOrderId);
                options.put("razorpay_payment_id", razorpayPaymentId);
                options.put("razorpay_signature", razorpaySignature);

                boolean isValidSignature = Utils.verifyPaymentSignature(options, razorpayKeySecret);
                if (!isValidSignature) {
                    throw new APIException("Payment Verification Failed: Invalid Signature!");
                }
            } catch (RazorpayException e) {
                throw new APIException("Error verifying payment: " + e.getMessage());
            }
        }

        // 2. IDEMPOTENCY: Prevent replay attacks / double-processing the same payment
        if (paymentRepository.existsByRazorpayOrderId(razorpayOrderId)) {
            throw new APIException("Duplicate Transaction: This order has already been processed!");
        }

        // 3. Load cart and address
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        List<CartItem> cartItems = cart.getCartItems();
        if (cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        // 4. Create and persist the Order
        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(cart.getTotalPrice());
        order.setOrderStatus("Payment Confirmed");
        order.setAddress(address);

        // 5. Save payment record (includes razorpay IDs for idempotency)
        Payment payment = new Payment(
                paymentMethod, razorpayPaymentId, pgStatus,
                pgResponseMessage, pgName, razorpayOrderId, razorpaySignature
        );
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);

        // 6. Create OrderItems and reduce product stock — batch saves (no per-item queries)
        List<OrderItem> orderItems = new ArrayList<>();
        List<Product> productsToUpdate = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItem.setOrder(savedOrder);
            // Preserve variation selections in order history
            orderItem.setSelectedSize(cartItem.getSelectedSize());
            orderItem.setSelectedColor(cartItem.getSelectedColor());
            orderItems.add(orderItem);

            // Decrement stock
            Product product = cartItem.getProduct();
            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            productsToUpdate.add(product);
        }
        orderItems = orderItemRepository.saveAll(orderItems);
        productRepository.saveAll(productsToUpdate);

        // 7. Clear the cart after successful order
        cartService.clearCart(cart.getCartId());

        // 8. Build response DTO
        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        orderItems.forEach(item ->
                orderDTO.getOrderItems().add(modelMapper.map(item, OrderItemDTO.class)));
        orderDTO.setAddressId(addressId);

        return orderDTO;
    }


    // =========================================================================
    // ADMIN: Get ALL orders with pagination
    // ✅ Uses findAllWithDetails() — loads all associations in 1 query via EntityGraph
    // =========================================================================
    @Override
    public OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sort = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sort);

        // ✅ EntityGraph-backed query: fetches orders + items + products + payment + address in 1 SQL
        Page<Order> pageOrders = orderRepository.findAllWithDetails(pageDetails);
        List<OrderDTO> orderDTOs = pageOrders.getContent().stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }


    // =========================================================================
    // ADMIN: Update order status + notify customer via email
    // =========================================================================
    @Override
    public OrderDTO updateOrder(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));
        order.setOrderStatus(status);
        orderRepository.save(order);

        // Fire async email — any SMTP failure is logged in EmailService, does not block the response
        emailService.sendOrderStatusUpdateEmail(order.getEmail(), orderId, status);

        return modelMapper.map(order, OrderDTO.class);
    }


    // =========================================================================
    // SELLER: Get orders belonging to the logged-in seller
    // ✅ FIXED: Filtering happens at DB level via JPQL JOIN — not in Java memory
    // ✅ FIXED: Pagination metadata (totalElements, totalPages) is now accurate
    // =========================================================================
    @Override
    public OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sort = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sort);

        User seller = authUtil.loggedInUser();

        // ✅ DB-level filter: only returns orders that contain at least one product by this seller
        Page<Order> pageOrders = orderRepository.findOrdersBySellerId(seller.getUserId(), pageDetails);

        List<OrderDTO> orderDTOs = pageOrders.getContent().stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setContent(orderDTOs);
        orderResponse.setPageNumber(pageOrders.getNumber());
        orderResponse.setPageSize(pageOrders.getSize());
        orderResponse.setTotalElements(pageOrders.getTotalElements());  // ✅ Now accurately reflects seller's orders
        orderResponse.setTotalPages(pageOrders.getTotalPages());
        orderResponse.setLastPage(pageOrders.isLast());
        return orderResponse;
    }


    // =========================================================================
    // USER: Get all orders for the logged-in user (order tracking)
    // ✅ FIXED: Uses EntityGraph — loads all data in 1 query, not N+1
    // =========================================================================
    @Override
    public List<OrderDTO> getUserOrders(String emailId) {
        // ✅ Single query via @EntityGraph — no lazy-loading surprises from ModelMapper
        List<Order> orders = orderRepository.findByEmail(emailId);
        return orders.stream()
                .map(order -> modelMapper.map(order, OrderDTO.class))
                .toList();
    }
}
