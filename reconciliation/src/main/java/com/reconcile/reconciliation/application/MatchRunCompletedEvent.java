package com.reconcile.reconciliation.application;

import com.reconcile.reconciliation.domain.MatchRunId;
import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantScopedEvent;

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
