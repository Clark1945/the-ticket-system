# API Gateway

## Purpose
- Single entry point for all client requests
- Route requests to downstream services
- Verify JWT for protected routes

## Tech Stack
- Java Spring Cloud Gateway (Use Java 17 Spring Boot 3 Maven)
- Redis
- ELK Stack

## Port
- API Gateway: 8080
- Auth Service: 8081

## Routing
| Path      | Destination           |
|-----------|-----------------------|
| /auth/**  | http://localhost:8081 |
| /app/**   | http://localhost:8090 |

## JWT Whitelist (No verification required)
POST /auth/merchant/register
POST /auth/merchant/login
POST /auth/merchant/otp/resend
POST /auth/merchant/email-verify
GET  /auth/oauth2/{provider}
GET  /auth/oauth2/{provider}/callback

## Cross-Cutting Concerns
- Rate Limiting: 60 requests/min per IP (Redis Sliding Window Log)
- Request Logging: method, path, status, latency (api_call_log save to ELK)
- Request Timeout: 5s per downstream service
- Error Response Format: { code, message, timestamp }
  - code: string e.g. "TOKEN_INVALID", "RATE_LIMIT_EXCEEDED"
  - timestamp: ISO 8601 e.g. "2026-03-07T10:00:00Z"
  - HTTP 429 when rate limit exceeded

## Api call log
- timestamp    → timestamp
- method       → GET / POST
- path         → /auth/merchant/login
- status       → 200 / 401 / 404
- latency      → 100ms
- ip_address   → 127.0.0.1

## JWT Process after verification
- get actor_id, actor_type from JWT payload(Stored in cookie)
- Add them into Request Header (X-Actor-Id, X-Actor-Type)
- Send request to downstream service

## Redis
- key: rate_limit:{ip}
- ZSET Score: timestamp
- ZSET Value: request_id
- LRU mechanism while Redis Rate Limiting

## Installation guide
### Windows
1. docker compose up -d
2. Copy-Item D:\Code\claude_all\the-ticket-system\auth-service\.env.example `
  D:\Code\claude_all\the-ticket-system\auth-service\.env
3. Copy-Item D:\Code\claude_all\the-ticket-system\api-gateway\.env.example `
  D:\Code\claude_all\the-ticket-system\api-gateway\.env
4. cd D:\Code\claude_all\the-ticket-system\auth-service
.\mvnw.cmd spring-boot:run
5. cd D:\Code\claude_all\the-ticket-system\api-gateway
.\mvnw.cmd spring-boot:run
6. cd D:\Code\claude_all\the-ticket-system\frontend-service
.\mvnw.cmd spring-boot:run

### Linux
1. docker compose up -d
2. cp auth-service/.env.example auth-service/.env
3. cp api-gateway/.env.example api-gateway/.env
4. cd D:\Code\claude_all\the-ticket-system\auth-service
.\mvnw.cmd spring-boot:run
5. cd D:\Code\claude_all\the-ticket-system\api-gateway
.\mvnw.cmd spring-boot:run
6. cd D:\Code\claude_all\the-ticket-system\frontend-service
.\mvnw.cmd spring-boot:run

## Future Improvements
- Circuit Breaker (Resilience4j)
- API Versioning (/v1/**)
- Health Check (/actuator/health)