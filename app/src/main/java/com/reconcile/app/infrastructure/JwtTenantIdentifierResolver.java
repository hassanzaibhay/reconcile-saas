package com.reconcile.app.infrastructure;

import com.reconcile.shared.domain.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class JwtTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    // Flips after ApplicationReadyEvent (after runners complete).
    // allow_jdbc_metadata_access=false means the bootstrap sentinel ("public") is never used
    // to open a real connection during EMF init. ApplicationRunners (TenantMigrationRunner) also
    // run before this event and legitimately need "public" for registry queries.
    private volatile boolean applicationReady = false;

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        applicationReady = true;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (!applicationReady) {
            return "public";
        }
        // Fail closed: throws MissingTenantException when TenantContext is not set.
        return TenantContext.current().schemaName();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
