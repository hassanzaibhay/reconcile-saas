package com.reconcile.reconciliation.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.reconcile.reconciliation.domain.MatchRunId;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantScopedEvent;

// Spring Modulith serializes domain events to JSON for the event_publication outbox.
// Record-style accessors (tenantId(), matchRunId()) are not detected by Jackson's default
// getter heuristic (getXxx); ANY field visibility lets Jackson reach private fields directly.
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MatchRunCompletedEvent extends TenantScopedEvent {

    private final MatchRunId matchRunId;

    public MatchRunCompletedEvent(MatchRunId matchRunId) {
        super(TenantContext.current());
        this.matchRunId = matchRunId;
    }

    public MatchRunId matchRunId() {
        return matchRunId;
    }
}
