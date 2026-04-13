package com.ecommerce.project.repositories;

import com.ecommerce.project.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT COALESCE(SUM(o.totalAmount),0) FROM Order o")
    Double getTotalRevenue();

    // ✅ FIX: Single JOIN query — replaces N+1 for GET /api/user/orders
    // Without this, fetching 10 orders with 3 items each = ~131 SQL queries.
    // With this, it's exactly 1 query regardless of order/item count.
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product", "payment", "address"})
    List<Order> findByEmail(String email);

    // ✅ FIX: Paginated admin orders — loads all associations in one query
    // Replaces the old findAll(pageable) which caused N+1 on every page load
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product", "payment", "address"})
    @Query("SELECT o FROM Order o")
    Page<Order> findAllWithDetails(Pageable pageable);

    // ✅ FIX: Seller orders — filters at DB level, not in Java memory
    // The old code fetched ALL orders then streamed/filtered in Java.
    // This means pagination was completely broken (page said "50 total" but only 3 belonged to seller).
    // DISTINCT prevents duplicate rows from the JOIN.
    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems oi JOIN oi.product p WHERE p.user.userId = :sellerId")
    Page<Order> findOrdersBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);
}
