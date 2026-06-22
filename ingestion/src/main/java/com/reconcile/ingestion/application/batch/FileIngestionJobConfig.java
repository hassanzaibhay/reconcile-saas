package com.reconcile.ingestion.application.batch;

import com.reconcile.ingestion.application.TenantJobExecutionListener;
import com.reconcile.ingestion.domain.LedgerEntryDraft;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class FileIngestionJobConfig {

    public static final String JOB_NAME = "fileIngestionJob";

    @Bean
    public Job fileIngestionJob(
            JobRepository jobRepository, Step csvIngestionStep, TenantJobExecutionListener tenantListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(tenantListener)
                .start(csvIngestionStep)
                .build();
    }

    @Bean
    public Step csvIngestionStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            FlatFileItemReader<Map<String, String>> csvReader,
            CsvRowProcessor processor,
            LedgerEntryWriter writer) {
        return new StepBuilder("csvIngestionStep", jobRepository)
                .<Map<String, String>, LedgerEntryDraft>chunk(100, txManager)
                .reader(csvReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Map<String, String>> csvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<Map<String, String>>()
                .name("csvReader")
                .resource(new FileSystemResource(filePath))
                .delimited()
                .names("date", "amount", "currency", "description", "reference", "feed_id")
                .fieldSetMapper(fieldSet -> Map.of(
                        "date", fieldSet.readString("date"),
                        "amount", fieldSet.readString("amount"),
                        "currency", fieldSet.readString("currency"),
                        "description", fieldSet.readString("description"),
                        "reference", fieldSet.readString("reference"),
                        "feed_id", fieldSet.readString("feed_id")))
                .linesToSkip(1)
                .build();
    }
}
