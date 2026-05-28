-- ============================================================
-- V5: Enforce tenant scoping for existing single-tenant tables
-- ============================================================

-- Keep this migration resilient for environments that already accepted
-- legacy webhook rows after V4 but before tenant_id was assigned in code.
INSERT INTO tenants (id, name, slug, plan, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Default Tenant',
    'default',
    'PRO',
    TRUE
) ON CONFLICT (slug) DO NOTHING;

UPDATE engineering_events
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

UPDATE engineers
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

UPDATE user_platform_identities
SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

ALTER TABLE engineering_events
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE engineers
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE user_platform_identities
    ALTER COLUMN tenant_id SET NOT NULL;

-- Remove old global uniqueness constraints so two tenants can have the same
-- engineer email or the same external platform user ID.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'engineers'
          AND c.contype = 'u'
          AND (
              SELECT array_agg(a.attname::text ORDER BY u.ordinality)
              FROM unnest(c.conkey) WITH ORDINALITY AS u(attnum, ordinality)
              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum
          ) = ARRAY['email']
    LOOP
        EXECUTE format('ALTER TABLE public.engineers DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'user_platform_identities'
          AND c.contype = 'u'
          AND (
              SELECT array_agg(a.attname::text ORDER BY u.ordinality)
              FROM unnest(c.conkey) WITH ORDINALITY AS u(attnum, ordinality)
              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum
          ) = ARRAY['platform', 'platform_user_id']
    LOOP
        EXECUTE format('ALTER TABLE public.user_platform_identities DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_engineers_tenant_email_unique
    ON engineers(tenant_id, email)
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_identities_tenant_platform_user_unique
    ON user_platform_identities(tenant_id, platform, platform_user_id);
