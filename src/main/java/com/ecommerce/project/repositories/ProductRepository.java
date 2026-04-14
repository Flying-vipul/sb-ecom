package com.ecommerce.project.repositories;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product,Long>, JpaSpecificationExecutor<Product> {
    // Same name, but now it forces isActive = true
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.isActive = true ORDER BY p.price ASC")
    Page<Product> findByCategoryOrderByPriceAsc(Category category, Pageable pageDetails);

    // Same name, but now it forces isActive = true
    @Query("SELECT p FROM Product p WHERE LOWER(p.productName) LIKE LOWER(:keyword) AND p.isActive = true")
    Page<Product> findByProductNameLikeIgnoreCase(String keyword, Pageable pageDetails);

    // Same name, but now it forces isActive = true
    @Query("SELECT p FROM Product p WHERE p.isFeatured = true AND p.isActive = true")
    List<Product> findByIsFeaturedTrue();

    // Same name, but now it forces isActive = true
    @Query("SELECT p FROM Product p WHERE p.user = :user AND p.isActive = true")
    Page<Product> findByUser(User user, Pageable pageDetails);
}
