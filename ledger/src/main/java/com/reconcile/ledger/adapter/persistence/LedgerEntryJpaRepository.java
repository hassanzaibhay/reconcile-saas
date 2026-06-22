package com.reconcile.ledger.adapter.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    @Query(
            "SELECT e FROM LedgerEntryEntity e WHERE e.feedId = :feedId"
                    + " AND e.entryDate >= :from AND e.entryDate <= :to")
    List<LedgerEntryEntity> findByFeedIdAndDateRange(
            @Param("feedId") String feedId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
