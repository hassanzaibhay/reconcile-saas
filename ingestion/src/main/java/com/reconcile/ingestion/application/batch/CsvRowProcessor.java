package com.reconcile.ingestion.application.batch;

import com.reconcile.ingestion.domain.LedgerEntryDraft;
import com.reconcile.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/** Maps a raw CSV row (map of column → value) to a {@link LedgerEntryDraft}. */
@Component
public class CsvRowProcessor implements ItemProcessor<java.util.Map<String, String>, LedgerEntryDraft> {

    @Override
    public LedgerEntryDraft process(java.util.Map<String, String> row) {
        String feedId = row.getOrDefault("feed_id", "default");
        LocalDate date = LocalDate.parse(row.get("date"));
        BigDecimal amount = new BigDecimal(row.get("amount"));
        String currency = row.getOrDefault("currency", "USD");
        String description = row.get("description");
        String reference = row.get("reference");
        return new LedgerEntryDraft(
                feedId, date, Money.of(amount, Currency.getInstance(currency)), description,
                reference);
    }
}
