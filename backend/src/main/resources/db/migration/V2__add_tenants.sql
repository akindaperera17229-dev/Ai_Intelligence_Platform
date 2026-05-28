-- ============================================================
-- V2: Create multi-tenant core tables
-- ============================================================

-- Enable UUID generation (PostgreSQL 13+ has gen_random_uuid() built-in)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- tenants: one row per company or individual user account
-- ============================================================
CREATE TABLE IF NOT EXISTS tenants (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) UNIQUE NOT NULL,    -- URL-friendly identifier, e.g. "techcorp"
    plan        VARCHAR(50)  NOT NULL DEFAULT 'FREE',  -- FREE / PRO / ENTERPRISE
    github_id   VARCHAR(100) UNIQUE,             -- GitHub org/user numeric ID (from OAuth)
    avatar_url  VARCHAR(500),                    -- GitHub avatar for UI display
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- tenant_users: login accounts — one admin per tenant
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    github_id       VARCHAR(100) UNIQUE NOT NULL,  -- GitHub numeric user ID (immutable key)
    github_login    VARCHAR(100) NOT NULL,          -- GitHub username (can change, display only)
    email           VARCHAR(255),
    avatar_url      VARCHAR(500),
    role            VARCHAR(50)  NOT NULL DEFAULT 'ADMIN',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- tenant_credentials: per-tenant API tokens, AES-256 encrypted
-- ============================================================
CREATE TABLE IF NOT EXISTS tenant_credentials (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    platform         VARCHAR(50)  NOT NULL,   -- JIRA / GITHUB / SLACK
    credential_key   VARCHAR(100) NOT NULL,   -- base_url / user_email / api_token / webhook_secret
    credential_value TEXT         NOT NULL,   -- AES-256-GCM encrypted value (Base64)
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, platform, credential_key)
);

-- ============================================================
-- Indexes for performance
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant_id   ON tenant_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_creds_tenant_id   ON tenant_credentials(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_creds_lookup      ON tenant_credentials(tenant_id, platform, credential_key);
