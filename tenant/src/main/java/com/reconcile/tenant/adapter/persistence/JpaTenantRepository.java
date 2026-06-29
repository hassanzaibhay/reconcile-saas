package com.reconcile.tenant.adapter.persistence;

import com.reconcile.shared.domain.TenantId;
import com.reconcile.tenant.domain.Tenant;
import com.reconcile.tenant.domain.TenantRepository;
import com.reconcile.tenant.domain.TenantStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Registry operations target public.tenants (fully-qualified), bypassing multi-tenant routing.
@Repository
class JpaTenantRepository implements TenantRepository {

    private static final String INSERT = "INSERT INTO public.tenants (id, slug, status, created_at) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT (id) DO UPDATE SET slug = EXCLUDED.slug, status = EXCLUDED.status";
    private static final String SELECT_ALL = "SELECT id, slug, status, created_at FROM public.tenants";

    private final JdbcTemplate jdbc;

    JpaTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Tenant tenant) {
        jdbc.update(
                INSERT, tenant.id().value(), tenant.slug(), tenant.status().name(), Timestamp.from(tenant.createdAt()));
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jdbc.query(SELECT_ALL + " WHERE id = ?", this::mapRow, id.value()).stream()
                .findFirst();
    }

    @Override
    public Optional<Tenant> findBySlug(String slug) {
        return jdbc.query(SELECT_ALL + " WHERE slug = ?", this::mapRow, slug).stream()
                .findFirst();
    }

    @Override
    public List<Tenant> findAllActive() {
        return jdbc.query(SELECT_ALL + " WHERE status = 'ACTIVE'", this::mapRow);
    }

    private Tenant mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Tenant(
                TenantId.of((UUID) rs.getObject("id")),
                rs.getString("slug"),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
