package com.reconcile.notification.application;

import com.reconcile.notification.domain.Notification;

public interface NotificationStore {
    void save(Notification notification);
}
