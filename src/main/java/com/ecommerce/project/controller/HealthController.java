package com.ecommerce.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health check endpoint — used by cron-job.org to ping the server
 * every 14 minutes so Render Free Tier never goes to sleep (cold start bypass).
 * Setup:
 *  1. Deploy this to Render.
 *  2. Go to https://cron-job.org → Create cronjob
 *     URL:  https://your-backend.onrender.com/api/health
 *     Schedule: Every 14 minutes
 *  3. That's it — server stays warm 24/7 within Render's 750 free hours/month.
 */
@RestController
public class HealthController {

    private static final long START_TIME = System.currentTimeMillis();

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> keepAwake() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("message", "Zappit backend is warm and ready!");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("uptimeSeconds", (System.currentTimeMillis() - START_TIME) / 1000);
        return ResponseEntity.ok(health);
    }
}
