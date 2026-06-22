package com.reconcile.ingestion.application;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import java.util.UUID;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Establishes {@link TenantContext} from the {@code tenantId} job parameter before each job, and
 * clears it after. Registered on every Spring Batch job that runs against a tenant schema.
 */
@Component
public class TenantJobExecutionListener implements JobExecutionListener {

    public static final String TENANT_ID_PARAM = "tenantId";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String tenantId = jobExecution.getJobParameters().getString(TENANT_ID_PARAM);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Job parameter '" + TENANT_ID_PARAM + "' is required but missing");
        }
        TenantContext.set(TenantId.of(UUID.fromString(tenantId)));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        TenantContext.clear();
    }
}
