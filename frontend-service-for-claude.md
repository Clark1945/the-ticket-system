# BFF Service

## Purpose
- Serve HTML pages for both User and Merchant (Thymeleaf SSR)
- Aggregate data from downstream services
- Manage session state

## Tech Stack
- Java 17 Spring Boot 3
- Thymeleaf
- Spring Session + Redis
- WebClient (HTTP Client)
- Spring Security (CSRF protection built-in)

## Port
- BFF: 8090

## Page Routes

### User Pages
GET /app/user/login         → User login page
GET /app/user/home          → User home page

### Merchant Pages
GET /app/merchant/register      → Merchant registration page
GET /app/merchant/email-verify  → OTP verification page
GET /app/merchant/login         → Merchant login page
GET /app/merchant/home          → Merchant home page

## Page Guard Rules

### Unauthenticated Access
| Access Path           | Redirect To              |
|-----------------------|--------------------------|
| /app/merchant/**      | /app/merchant/login      |
| /app/user/**          | /app/user/login          |

### Wrong Role Access
| Actor    | Access Path      | Redirect To         |
|----------|------------------|---------------------|
| MERCHANT | /app/user/**     | /app/user/login     |
| USER     | /app/merchant/** | /app/merchant/login |

### Already Logged In
| Actor    | Access Path           | Redirect To          |
|----------|-----------------------|----------------------|
| MERCHANT | /app/merchant/login   | /app/merchant/home   |
| USER     | /app/user/login       | /app/user/home       |

### Direct Access Without Flow
| Access Path                 | Redirect To              |
|-----------------------------|--------------------------|
| /app/merchant/email-verify  | /app/merchant/register   |

## Session Design (Redis)
- Strategy: Sliding Window (TTL reset on every request)

| Key                   | Value                                          | TTL     |
|-----------------------|------------------------------------------------|---------|
| session:{session_id}  | { actor_id, actor_type, actor_name, email }    | 30 mins |
| pending:email:{email} | email (merchant registration pending OTP)      | 10 mins |

## Token & Session Sync

### access_token Expired (Session still alive)
BFF receives 401 from downstream
→ POST /auth/token/refresh
→ Success: update Cookie, retry original request
→ Failure: clear Session, redirect to login page

### Session Expired (JWT still alive)
API Gateway passes JWT verification
→ BFF cannot find Session
→ Redirect to login page

### Logout
BFF → POST /auth/logout
→ Clear Redis Session
→ JWT added to Blacklist
→ Redirect to login page

## Downstream Communication
- BFF uses X-Actor-Id / X-Actor-Type headers injected by API Gateway
- BFF does NOT parse JWT directly
- All downstream requests forwarded with X-Actor-Id / X-Actor-Type headers

## Downstream Services
| Service        | Destination           | Timeout |
|----------------|-----------------------|---------|
| Auth Service   | http://localhost:8081 | 4s      |
| Ticket Service | http://localhost:8082 | 4s      |

## Error Handling
| Status        | Behavior                                           |
|---------------|----------------------------------------------------|
| 401           | Trigger token refresh → retry → redirect to login  |
| 403           | Render 403 error page                              |
| 404           | Render 404 error page                              |
| 503           | Render 503 error page                              |
| Timeout (>4s) | Render 503 error page                              |

## Future Improvements
- Cache frequently accessed pages (Redis)
- Circuit Breaker (Resilience4j)