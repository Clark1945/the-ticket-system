# The Ticket System

A microservices-based ticket management platform built with Spring Boot 3 and Spring Cloud Gateway.

## Architecture Overview

```
Browser
  │
  ▼
API Gateway (8080)   ← JWT verification, Rate Limiting, Request Logging
  ├──/auth/**──────► Auth Service (8081)   ← Merchant & User Authentication
  └──/app/**───────► Frontend BFF (8090)  ← Thymeleaf SSR, Session Management
                             │
                             └──────────► Ticket Service (8082) [coming soon]
```

## Services

| Service          | Port | Description |
|------------------|------|-------------|
| API Gateway      | 8080 | Single entry point — JWT auth, rate limiting (60 req/min), ELK logging |
| Auth Service     | 8081 | Merchant Basic Auth + Email OTP, User OAuth2 (Google / LINE / GitHub) |
| Frontend BFF     | 8090 | Thymeleaf pages, Redis session, token refresh logic |
| Ticket Service   | 8082 | *(coming soon)* |

## Tech Stack

- **Language / Framework** — Java 17, Spring Boot 3.3.4, Spring Cloud Gateway, Maven
- **Database** — PostgreSQL (Flyway migrations)
- **Cache / Session** — Redis
- **Auth** — JWT (HttpOnly cookies), OAuth2, BCrypt, Email OTP (Gmail SMTP)
- **Logging** — ELK Stack (Elasticsearch + Logstash + Kibana)
- **Template Engine** — Thymeleaf (SSR)

## Authentication Flows

### Merchant Registration
```
Register (email + password)
  → Auth Service sends OTP via Gmail
  → POST /auth/merchant/email-verify/REGISTER
  → Account activated
```

### Merchant Login
```
Login (email + password)
  → Auth Service sends OTP via Gmail
  → POST /auth/merchant/email-verify/LOGIN
  → JWT issued (access_token 30min / refresh_token 7 days)
```

### User Login (OAuth2)
```
GET /auth/oauth2/{provider}  (provider: google | line | github)
  → Redirect to OAuth2 provider
  → Callback → Auth Service issues JWT
  → Redirect to BFF home page
```

## Security Design

- JWT delivered via **HttpOnly, SameSite=Strict** cookies
- **Refresh token rotation** — new refresh token issued on every refresh
- **JWT blacklist** in Redis on logout (TTL = remaining access token lifetime)
- **Rate limiting** — 60 requests/min per IP (Redis Sliding Window)
- API Gateway injects `X-Actor-Id` and `X-Actor-Type` headers to downstream services

## Local Setup

### Prerequisites

- Java 17+
- Maven
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, and the ELK stack.

### 2. Configure environment variables

```bash
# Linux / macOS
cp auth-service/.env.example  auth-service/.env
cp api-gateway/.env.example   api-gateway/.env

# Windows (PowerShell)
Copy-Item auth-service\.env.example  auth-service\.env
Copy-Item api-gateway\.env.example   api-gateway\.env
```

Edit `auth-service/.env` and fill in the required values:

| Variable | Description |
|----------|-------------|
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials |
| `JWT_SECRET` | Random string, min 32 characters (must match api-gateway) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail address + [App Password](https://myaccount.google.com/apppasswords) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials |
| `LINE_CLIENT_ID` / `LINE_CLIENT_SECRET` | LINE Login credentials |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth App credentials |
| `BFF_REDIRECT_URL` | `http://localhost:8080/app/oauth2/callback` |

Edit `api-gateway/.env`:

| Variable | Description |
|----------|-------------|
| `JWT_SECRET` | Must be identical to auth-service |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection |

### 3. Run the services

Open three terminals:

```bash
# Terminal 1 — Auth Service
cd auth-service
./mvnw spring-boot:run        # Linux/macOS
.\mvnw.cmd spring-boot:run    # Windows

# Terminal 2 — API Gateway
cd api-gateway
./mvnw spring-boot:run
.\mvnw.cmd spring-boot:run

# Terminal 3 — Frontend BFF
cd frontend-service
./mvnw spring-boot:run
.\mvnw.cmd spring-boot:run
```

### 4. Open the app

| URL | Description |
|-----|-------------|
| http://localhost:8080/app/merchant/register | Merchant registration |
| http://localhost:8080/app/merchant/login | Merchant login |
| http://localhost:8080/app/user/login | User login (OAuth2) |

> All traffic goes through the API Gateway on port 8080.

## Project Structure

```
the-ticket-system/
├── api-gateway/          # Spring Cloud Gateway
│   └── src/main/java/com/ticketsystem/gateway/
│       ├── filter/       # JWT auth, rate limit, request logging
│       └── service/      # JWT parsing
├── auth-service/         # Authentication & Authorization
│   └── src/main/java/com/ticketsystem/auth/
│       ├── controller/   # Merchant, User, Common, Me endpoints
│       ├── service/      # Auth logic, JWT, OTP, Email
│       ├── entity/       # JPA entities
│       └── oauth2/       # OAuth2 success handler
├── frontend-service/     # BFF — Thymeleaf SSR
│   └── src/main/java/com/ticketsystem/frontend/
│       ├── controller/   # Page controllers
│       ├── interceptor/  # PageGuardInterceptor (auth/role/redirect)
│       └── service/      # AuthClientService, SessionService
└── docker-compose.yml    # PostgreSQL + Redis + ELK
```

## API Reference

### Auth Service (`/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/merchant/register` | Public | Register merchant |
| POST | `/auth/merchant/login` | Public | Merchant login (sends OTP) |
| POST | `/auth/merchant/email-verify/{action}` | Public | Verify OTP (`REGISTER` or `LOGIN`) |
| POST | `/auth/merchant/otp/resend` | Public | Resend OTP |
| GET | `/auth/oauth2/{provider}` | Public | Start OAuth2 login |
| GET | `/auth/me` | Cookie | Get current actor info |
| POST | `/auth/token/refresh` | Cookie | Refresh access token |
| POST | `/auth/logout` | Cookie | Logout and blacklist token |

### Frontend BFF (`/app`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/app/merchant/register` | Registration page |
| GET | `/app/merchant/email-verify` | OTP verification page |
| GET | `/app/merchant/login` | Login page |
| GET | `/app/merchant/home` | Merchant home (protected) |
| GET | `/app/user/login` | User login page |
| GET | `/app/user/home` | User home (protected) |
| GET | `/app/oauth2/callback` | OAuth2 callback handler |

## Database Schema

See [`auth-service/src/main/resources/db/migration/V1__init.sql`](auth-service/src/main/resources/db/migration/V1__init.sql)

Tables: `merchants`, `users`, `user_auth_providers`, `merchant_refresh_tokens`, `user_refresh_tokens`, `audit_logs`

## Future Improvements

- Ticket Service (core business feature)
- OTP brute-force protection (lock after N failed attempts)
- Circuit Breaker (Resilience4j)
- API Versioning (`/v1/**`)
- Health Check endpoints (`/actuator/health`)
- Redis Cluster & Sentinel for high availability
- Kubernetes deployment with auto-scaling
