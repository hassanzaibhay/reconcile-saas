package com.reconcile.iam.application;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates {@link TenantContext} from the submitting thread to the worker thread. Apply to every
 * {@code ThreadPoolTaskExecutor} bean (default async, batch, event publisher). Without this, async
 * tasks throw {@link com.reconcile.shared.domain.MissingTenantException}.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        TenantId captured = TenantContext.isSet() ? TenantContext.current() : null;
        return () -> {
            try {
                if (captured != null) {
                    TenantContext.set(captured);
                }
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
