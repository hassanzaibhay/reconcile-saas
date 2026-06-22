package com.reconcile.ingestion.adapter.web;

import com.reconcile.ingestion.application.TenantJobExecutionListener;
import com.reconcile.ingestion.application.batch.FileIngestionJobConfig;
import com.reconcile.shared.domain.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ingestion")
class IngestionController {

    private final JobLauncher jobLauncher;
    private final Job fileIngestionJob;
    private final StringRedisTemplate redis;

    IngestionController(
            JobLauncher jobLauncher,
            @Qualifier(FileIngestionJobConfig.JOB_NAME) Job fileIngestionJob,
            StringRedisTemplate redis) {
        this.jobLauncher = jobLauncher;
        this.fileIngestionJob = fileIngestionJob;
        this.redis = redis;
    }

    @PostMapping("/files")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a transaction file for ingestion")
    IngestionResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("feedId") String feedId,
            @RequestHeader("Idempotency-Key") String idempotencyKey)
            throws Exception {

        validateIdempotencyKey(idempotencyKey);
        String tenantId = TenantContext.current().toString();
        String redisKey = "idempotency:" + tenantId + ":" + idempotencyKey;

        String existing = redis.opsForValue().get(redisKey);
        if (existing != null) {
            return new IngestionResponse(existing, "DUPLICATE");
        }

        String contentHash = sha256(file.getBytes());
        String ingestionRunId = UUID.randomUUID().toString();
        redis.opsForValue().set(redisKey, ingestionRunId, 24, TimeUnit.HOURS);

        File tempFile = File.createTempFile("ingestion-", ".csv");
        file.transferTo(tempFile);

        JobParameters params = new JobParametersBuilder()
                .addString(TenantJobExecutionListener.TENANT_ID_PARAM, tenantId, true)
                .addString("ingestionRunId", ingestionRunId, true)
                .addString("feedId", feedId)
                .addString("contentHash", contentHash)
                .addString("filePath", tempFile.getAbsolutePath())
                .toJobParameters();

        jobLauncher.run(fileIngestionJob, params);
        return new IngestionResponse(ingestionRunId, "ACCEPTED");
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a valid UUID");
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    record IngestionResponse(String ingestionRunId, String status) {}
}
