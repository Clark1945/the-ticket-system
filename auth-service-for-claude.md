# Auth Service

## Purpose
- Provide Merchant login & User login
- Merchant login use Basic Auth (username:password) + Email OTP(Gmail)
- User login use OAuth2 (Google, LINE, Github)

## Tech Stack
- Java 17 Spring Boot 3 Maven
- PostgreSQL
- Redis
- Docker Compose

## API Design

### User API
GET  /auth/oauth2/{provider}
GET  /auth/oauth2/{provider}/callback

### Merchant API
POST /auth/merchant/register
POST /auth/merchant/login
POST /auth/merchant/email-verify/{action}   action: REGISTER, LOGIN
POST /auth/merchant/otp/resend              body: { email }

### Common API
POST /auth/logout
POST /auth/token/refresh

## Token Strategy
- Delivery: Set-Cookie (HttpOnly, SameSite=Strict)
- access_token:  JWT, TTL 30 mins
- refresh_token: JWT, TTL 7 days

## Port 8081

## Database Design

### 1. merchants
| Column            | Type                       | Constraints              |
|-------------------|----------------------------|--------------------------|
| id                | BIGSERIAL                  | PK                       |
| name              | VARCHAR(255)               | NOT NULL                 |
| address           | VARCHAR(1024)              |                          |
| phone             | VARCHAR(20)                |                          |
| email             | VARCHAR(255)               | UNIQUE, NOT NULL         |
| password_hash     | VARCHAR(255)               | NOT NULL                 |
| is_email_verified | BOOLEAN                    | DEFAULT FALSE            |
| status            | VARCHAR(20)                | DEFAULT 'ACTIVE'         |
| created_at        | TIMESTAMP                  | DEFAULT NOW()            |
| updated_at        | TIMESTAMP                  | DEFAULT NOW()            |

status values: 'ACTIVE', 'SUSPENDED'

### 2. users
| Column     | Type         | Constraints        |
|------------|--------------|--------------------|
| id         | BIGSERIAL    | PK                 |
| name       | VARCHAR(255) |                    |
| address    | VARCHAR(1024)|                    |
| phone      | VARCHAR(20)  |                    |
| email      | VARCHAR(255) | UNIQUE             |
| status     | VARCHAR(20)  | DEFAULT 'ACTIVE'   |
| created_at | TIMESTAMP    | DEFAULT NOW()      |
| updated_at | TIMESTAMP    | DEFAULT NOW()      |

status values: 'ACTIVE', 'BANNED'

### 3. user_auth_providers
| Column           | Type         | Constraints        |
|------------------|--------------|--------------------|
| id               | BIGSERIAL    | PK                 |
| user_id          | BIGINT       | FK → users.id      |
| provider         | VARCHAR(20)  | NOT NULL           |
| provider_uid     | VARCHAR(255) | NOT NULL           |
| access_token     | TEXT         |                    |
| refresh_token    | TEXT         |                    |
| token_expires_at | TIMESTAMP    |                    |
| created_at       | TIMESTAMP    | DEFAULT NOW()      |
| updated_at       | TIMESTAMP    | DEFAULT NOW()      |

provider values: 'GOOGLE', 'LINE', 'Github'
UNIQUE (provider, provider_uid)

### 4. merchant_refresh_tokens
| Column        | Type         | Constraints        |
|---------------|--------------|--------------------|
| id            | BIGSERIAL    | PK                 |
| merchant_id   | BIGINT       | FK → merchants.id  |
| refresh_token | VARCHAR(255) | UNIQUE, NOT NULL   |
| expires_at    | TIMESTAMP    | NOT NULL           |
| created_at    | TIMESTAMP    | DEFAULT NOW()      |

### 5. user_refresh_tokens
| Column        | Type         | Constraints        |
|---------------|--------------|--------------------|
| id            | BIGSERIAL    | PK                 |
| user_id       | BIGINT       | FK → users.id      |
| refresh_token | VARCHAR(255) | UNIQUE, NOT NULL   |
| expires_at    | TIMESTAMP    | NOT NULL           |
| created_at    | TIMESTAMP    | DEFAULT NOW()      |

### 6. audit_logs
| Column     | Type         | Constraints        |
|------------|--------------|--------------------|
| id         | BIGSERIAL    | PK                 |
| actor_id   | BIGINT       | NOT NULL           |
| actor_type | VARCHAR(20)  | NOT NULL           |
| action     | VARCHAR(100) | NOT NULL           |
| detail     | JSON         |                    |
| ip_address | VARCHAR(45)  |                    |
| created_at | TIMESTAMP    | DEFAULT NOW()      |

actor_type values: 'MERCHANT', 'USER'
action values: 'LOGIN', 'LOGOUT', 'TOKEN_REFRESH'

## Cache Design (Redis)

### Merchant Email OTP
| Key                               | Value    | TTL  |
|-----------------------------------|----------|------|
| otp:merchant:{merchant_id}:{type} | OTP code | 600s |

### JWT Logout Blacklist (Merchant + User)
| Key                      | Value | TTL                    |
|--------------------------|-------|------------------------|
| blacklist:{access_token} | 1     | Access Token left time |

## Architecture

### User Login Flow
BFF -> GET /auth/oauth2/{provider}
-> Redirect to OAuth2 Provider authorization page
-> GET /auth/oauth2/{provider}/callback
-> Auth Service issues JWT (access_token + refresh_token)
-> Redirect to BFF index page

### Merchant Registration Flow
BFF -> POST /auth/merchant/register
-> Auth Service sends OTP via SMTP
-> POST /auth/merchant/email-verify/REGISTER (validate OTP)
-> is_email_verified = true
-> Redirect to BFF index page

### Merchant Login Flow
BFF -> POST /auth/merchant/login (validate credentials, send OTP)
-> POST /auth/merchant/email-verify/LOGIN (validate OTP)
-> Auth Service issues JWT (access_token + refresh_token)
-> Redirect to BFF index page

### Logout Flow
BFF -> POST /auth/logout
-> Redis blacklist: blacklist:{access_token} = 1, TTL = access_token left time

## Future Improvements
- OTP brute force protection (lock account after 5 failed attempts)
- Auto-scaling instance using Kubernetes
- Redis Cluster & Redis Sentinel for High Availability