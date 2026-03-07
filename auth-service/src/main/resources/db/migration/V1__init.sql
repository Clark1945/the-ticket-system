CREATE TABLE merchants
(
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255)  NOT NULL,
    address            VARCHAR(1024),
    phone              VARCHAR(20),
    email              VARCHAR(255)  NOT NULL UNIQUE,
    password_hash      VARCHAR(255)  NOT NULL,
    is_email_verified  BOOLEAN       NOT NULL DEFAULT FALSE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255),
    address    VARCHAR(1024),
    phone      VARCHAR(20),
    email      VARCHAR(255) UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_auth_providers
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_uid     VARCHAR(255) NOT NULL,
    access_token     TEXT,
    refresh_token    TEXT,
    token_expires_at TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_uid)
);

CREATE TABLE merchant_refresh_tokens
(
    id            BIGSERIAL PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_refresh_tokens
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs
(
    id         BIGSERIAL PRIMARY KEY,
    actor_id   BIGINT       NOT NULL,
    actor_type VARCHAR(20)  NOT NULL,
    action     VARCHAR(100) NOT NULL,
    detail     JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchant_refresh_tokens_merchant_id ON merchant_refresh_tokens (merchant_id);
CREATE INDEX idx_user_refresh_tokens_user_id ON user_refresh_tokens (user_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id, actor_type);
CREATE INDEX idx_user_auth_providers_user_id ON user_auth_providers (user_id);
