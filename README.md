# Zappit Backend API 🛒⚡

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Ready-blue.svg)](https://www.postgresql.org/)
[![Razorpay](https://img.shields.io/badge/Payment-Razorpay-blueviolet.svg)](https://razorpay.com/)

An enterprise-grade, monolithic REST API built to power the Zappit e-commerce ecosystem. Engineered for high concurrency and secure transactions, this backend serves as the central nervous system for user authentication, product cataloging, cart management, media handling, and financial processing.

🌐 **Live Frontend Integration:** [Zappit India](https://zappitindia.netlify.app/)

---

## 🏗️ Core Architecture & Features

* **Zero-Trust Security (JJWT):** Implements stateless JSON Web Token authentication with strict Role-Based Access Control (RBAC) for Admins, Sellers, and standard Users.
* **Cryptographic Payment Integrity:** Fully integrated with the Razorpay Java SDK. Utilizes a server-side two-phase commit with HMAC SHA-256 signature verification and database idempotency checks to neutralize Replay Attacks and frontend manipulation.
* **Flash-Sale Optimized:** Engineered with JPA database locking mechanisms to ensure ACID compliance during high-traffic checkout events, preventing negative inventory race conditions.
* **Cloud Media Management:** Integrated with the **Cloudinary** HTTP API for efficient product image uploading, transformation, and CDN delivery.
* **Data Decoupling:** Leverages **ModelMapper** for clean, decoupled data transfer between JPA Entities and DTOs, ensuring sensitive database fields are never exposed to the client.
* **Automated Communications:** Integrated `spring-boot-starter-mail` for asynchronous transactional emails (Order Confirmations, OTP verifications, Status Updates).
* **Secure Environment Management:** Utilizes `spring-dotenv` to keep sensitive credentials (DB URLs, Razorpay keys, SMTP passwords) securely out of the source code.

---

## 🧰 Tech Stack Deep Dive

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Java 21 | Leveraging modern JDK features like Records and Virtual Threads. |
| **Framework** | Spring Boot 3.5.7 | Core application framework and dependency injection. |
| **Security** | Spring Security 6 + JJWT 0.13.0 | Custom JWT filters and endpoint authorization. |
| **Persistence** | Spring Data JPA / Hibernate | ORM mapping and database interaction. |
| **Database** | PostgreSQL | Relational data storage. |
| **Payments** | Razorpay SDK (v1.4.6) | End-to-end payment gateway processing. |
| **Media Storage**| Cloudinary (v1.36.0) | Remote image hosting and management. |
| **Validation** | Jakarta Validation API | Strict DTO and Entity input validation. |

---

## 🚀 Local Development Setup

Follow these steps to boot the Zappit API on your local machine.

### 1. Prerequisites
* **JDK 21** installed and configured in your environment path.
* **Maven** installed.
* **PostgreSQL** installed and running on port `5432`.

### 2. Database Initialization
Log into your local PostgreSQL instance and create the database:
```sql
CREATE DATABASE ecom_zappit;
