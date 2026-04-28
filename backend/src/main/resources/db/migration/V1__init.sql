-- V1__init.sql: control-plane schema (users + databases)

CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_id    VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_clerk_id ON users (clerk_id);

CREATE TYPE database_status AS ENUM (
    'PROVISIONING',
    'ACTIVE',
    'DELETING',
    'DELETED',
    'FAILED'
);

CREATE TABLE databases (
    id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID            NOT NULL REFERENCES users(id),
    name              VARCHAR(63)     NOT NULL,
    pg_database_name  VARCHAR(63)     NOT NULL UNIQUE,
    pg_username       VARCHAR(63)     NOT NULL UNIQUE,
    pg_password_hash  TEXT            NOT NULL,
    status            database_status NOT NULL DEFAULT 'PROVISIONING',
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_databases_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_databases_user_id ON databases (user_id);
CREATE INDEX idx_databases_status  ON databases (status) WHERE status IN ('PROVISIONING', 'DELETING');
