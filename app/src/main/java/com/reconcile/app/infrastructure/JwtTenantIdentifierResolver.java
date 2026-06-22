package com.reconcile.app.infrastructure;

import com.reconcile.shared.domain.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
class JwtTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.current().schemaName();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
