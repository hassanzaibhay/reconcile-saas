package com.reconcile.notification.application;

import com.reconcile.notification.domain.Notification;
import com.reconcile.reconciliation.application.MatchRunCompletedEvent;
import com.reconcile.shared.domain.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class MatchRunNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MatchRunNotificationListener.class);

    private final NotificationStore store;

    public MatchRunNotificationListener(NotificationStore store) {
        this.store = store;
    }

    @ApplicationModuleListener
    public void on(MatchRunCompletedEvent event) {
        log.info("Match run completed: {} for tenant {}", event.matchRunId(), event.tenantId());
        Notification notification = Notification.of(
                TenantContext.current(), "MATCH_RUN_COMPLETED", "{\"runId\":\"" + event.matchRunId() + "\"}");
        store.save(notification);
    }
}
