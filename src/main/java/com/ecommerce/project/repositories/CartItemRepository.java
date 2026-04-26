package com.ecommerce.project.repositories;

import com.ecommerce.project.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartItemRepository  extends JpaRepository<CartItem, Long> {

    @Query("SELECT ci FROM CartItem ci WHERE ci.product.productId = ?1 AND ci.cart.cartId = ?2")
    CartItem findCartItemByProductIdAndCartId(Long productId, Long cartId);

    // Variation-aware lookup: handles NULL comparison for non-clothing products gracefully
    @Query("SELECT ci FROM CartItem ci WHERE ci.product.productId = :productId AND ci.cart.cartId = :cartId " +
           "AND (ci.selectedSize = :selectedSize OR (:selectedSize IS NULL AND ci.selectedSize IS NULL)) " +
           "AND (ci.selectedColor = :selectedColor OR (:selectedColor IS NULL AND ci.selectedColor IS NULL))")
    CartItem findCartItemByProductIdAndCartIdAndVariation(
            @Param("productId") Long productId,
            @Param("cartId") Long cartId,
            @Param("selectedSize") String selectedSize,
            @Param("selectedColor") String selectedColor);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.cartId = ?2 AND ci.product.productId = ?1")
    void deleteCartItemByProductIdAndCartId(Long productId, Long cartId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = ?1")
    void deleteAllByCartId(Long cartId);



    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.product.productId = :productId")
    void deleteAllByProductId(@Param("productId") Long productId);

    @Query("SELECT ci FROM CartItem ci WHERE ci.product.productId = :productId")
    List<CartItem> findCartItemsByProductId(@Param("productId") Long productId);



}
