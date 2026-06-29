package com.reconcile.notification.adapter.persistence;

import com.reconcile.notification.application.NotificationStore;
import com.reconcile.notification.domain.Notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.stereotype.Repository;

@Repository
class JpaNotificationStore implements NotificationStore {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Notification notification) {
        em.persist(new NotificationEntity(notification));
    }

    @Entity
    @Table(name = "audit_log")
    static class NotificationEntity {
        @Id
        UUID id;

        String actor = "SYSTEM";
        String action;

        @Column(name = "entity_type")
        String entityType = "NOTIFICATION";

        @Column(name = "entity_id")
        String entityId;

        // columnDefinition affects DDL only; @JdbcTypeCode(JSON) makes Hibernate bind the value
        // as a JSON type so PostgreSQL accepts it for the jsonb column (varchar cast is rejected).
        @Column(columnDefinition = "jsonb")
        @JdbcTypeCode(SqlTypes.JSON)
        String payload;

        @Column(name = "occurred_at")
        Instant occurredAt;

        protected NotificationEntity() {}

        NotificationEntity(Notification n) {
            this.id = n.id();
            this.action = n.type();
            this.entityId = n.id().toString();
            this.payload = n.payload();
            this.occurredAt = n.createdAt();
        }
    }
}
