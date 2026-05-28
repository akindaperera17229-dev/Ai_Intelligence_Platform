-- ============================================================
-- V3: Add tenant_id to existing event/engineer tables
-- ============================================================
-- We add columns as nullable first, then backfill in V4,
-- then enforce NOT NULL after backfill is safe.
-- ============================================================

ALTER TABLE engineering_events
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

ALTER TABLE engineers
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

ALTER TABLE user_platform_identities
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

-- Performance indexes — every query will filter by tenant_id
CREATE INDEX IF NOT EXISTS idx_events_tenant_id
    ON engineering_events(tenant_id);

CREATE INDEX IF NOT EXISTS idx_events_tenant_source
    ON engineering_events(tenant_id, source);

CREATE INDEX IF NOT EXISTS idx_events_tenant_timestamp
    ON engineering_events(tenant_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_engineers_tenant_id
    ON engineers(tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_identities_tenant_id
    ON user_platform_identities(tenant_id);
