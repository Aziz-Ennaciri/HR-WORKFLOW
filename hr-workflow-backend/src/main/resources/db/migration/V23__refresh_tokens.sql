-- Opaque refresh tokens; only a SHA-256 hash is stored, so a database leak does not leak sessions.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
