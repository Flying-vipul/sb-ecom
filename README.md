# Zappit Backend API 🛒⚡

An enterprise-grade, monolithic REST API built to power the Zappit e-commerce ecosystem. Engineered for high concurrency and secure transactions, this backend serves as the central nervous system for user authentication, product cataloging, cart management, and financial processing.

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Razorpay](https://img.shields.io/badge/Razorpay-02042B?style=for-the-badge&logo=razorpay&logoColor=3395FF)](https://razorpay.com/)

---

## 🏗️ Core Architecture & Features

* **Zero-Trust Security (JJWT):** Implements stateless JSON Web Token authentication with strict Role-Based Access Control (RBAC) for Admins, Sellers, and standard Users.
* **Cryptographic Payment Integrity:** Fully integrated with the Razorpay Java SDK. Utilizes a server-side two-phase commit with HMAC SHA-256 signature verification and database idempotency checks to neutralize Replay Attacks and frontend manipulation.
* **Flash-Sale Optimized:** Engineered with JPA database locking mechanisms to ensure ACID compliance during high-traffic checkout events, preventing negative inventory race conditions.
* **Object Mapping:** Leverages `ModelMapper` for clean, decoupled data transfer between JPA Entities and DTOs, ensuring sensitive database fields are never exposed to the client.
* **Automated Communications:** Integrated `spring-boot-starter-mail` for automated transactional emails (Order Confirmations, OTP verifications).

## 🧰 Tech Stack Deep Dive

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Java 21 | Leveraging modern JDK features like Records and Virtual Threads. |
| **Framework** | Spring Boot 3.5.7 | Core application framework. |
| **Security** | Spring Security 6 + JJWT 0.13.0 | Custom JWT filters and endpoint authorization. |
| **Persistence** | Spring Data JPA / Hibernate | ORM mapping and database interaction. |
| **Database** | PostgreSQL | Relational data storage (Driver explicitly configured). |
| **Payments** | Razorpay SDK (v1.4.6) | End-to-end payment gateway processing. |
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
