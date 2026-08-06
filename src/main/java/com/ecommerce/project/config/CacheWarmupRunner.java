package com.ecommerce.project.config;

import com.ecommerce.project.service.CategoryService;
import com.ecommerce.project.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * CacheWarmupRunner — runs automatically on every app startup.
 *
 * WHY: Redis starts empty. The first user to hit the site
 * would trigger DB queries (slow). By pre-filling Redis here,
 * the FIRST ever user gets the same 1ms Redis speed as the millionth user.
 *
 * WHAT it warms:
 *  - First 3 pages of products (most users never go past page 2)
 *  - All categories (used in navigation on EVERY page)
 *  - Featured products (shown on homepage)
 */
@Component
public class CacheWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmupRunner.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info(" Warming up Redis cache on startup...");

            // Warm categories — shown in nav bar on every single page
            categoryService.getAllCategories(0, 100, "categoryId", "asc");
            log.info("Categories cache warmed");

            // Warm featured products — shown on homepage hero section
            productService.getFeaturedProducts();
            log.info("Featured products cache warmed");

            // Warm first 3 pages of products (default sort — most common request)
            productService.getAllProducts(0, 20, "productId", "asc", null, null);
            productService.getAllProducts(1, 20, "productId", "asc", null, null);
            productService.getAllProducts(2, 20, "productId", "asc", null, null);
            log.info("Product pages 1-3 cache warmed");

            log.info("Redis cache fully warmed! First user will get instant responses.");

        } catch (Exception e) {
            // IMPORTANT: Never let warmup crash the app startup!
            // If Redis is not available or DB has no data yet, just log and continue.
            log.warn("Cache warmup failed (non-critical): {}. App will start normally.", e.getMessage());
        }
    }
}
