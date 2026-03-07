# Api Gateway


## Purpose
- BFF
- Entry for web accessibility through BFF
- Manage/store user JWT

## Tech Stack
- Use Java Spring Cloud Gateway for routing and JWT authentication

## Details
- POST /auth/merchant/register  → Do not verify JWT
- POST /auth/merchant/login     → Do not verify JWT
- POST /auth/merchant/email-verify → Do not verify JWT
- GET  /auth/oauth2/{provider}  → Do not verify JWT
- GET  /auth/oauth2/{provider}/callback → Do not verify JWT