-- Seed the demo tenant (registry row only; schema is created by TenantMigrationRunner at boot).
INSERT INTO public.tenants (id, slug, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo', 'ACTIVE', now())
ON CONFLICT (id) DO NOTHING;
