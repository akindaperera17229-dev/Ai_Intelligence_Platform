-- ============================================================
-- V4: Seed default tenant and backfill tenant_id columns
-- ============================================================
-- This migration creates the initial tenant for existing data
-- and assigns all existing events/engineers to it.
-- ============================================================

-- Create the default tenant (use a fixed UUID for reproducibility)
INSERT INTO tenants (id, name, slug, plan, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Default Tenant',
    'default',
    'PRO',
    TRUE
) ON CONFLICT (slug) DO NOTHING;

-- Backfill engineering_events: assign all existing rows to the default tenant
UPDATE engineering_events
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

-- Backfill engineers: assign all existing rows to the default tenant
UPDATE engineers
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

-- Backfill user_platform_identities: assign all existing rows to the default tenant
UPDATE user_platform_identities
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;
