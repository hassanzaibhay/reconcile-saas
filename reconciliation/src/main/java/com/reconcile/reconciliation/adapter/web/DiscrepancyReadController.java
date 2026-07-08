package com.reconcile.reconciliation.adapter.web;

import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.DiscrepancyForResolution;
import com.reconcile.reconciliation.application.DiscrepancyListQuery;
import com.reconcile.reconciliation.application.DiscrepancyListRow;
import com.reconcile.reconciliation.application.DiscrepancyQueryPort;
import com.reconcile.reconciliation.application.EntryMoney;
import com.reconcile.shared.web.pagination.Cursor;
import com.reconcile.shared.web.pagination.CursorCodec;
import com.reconcile.shared.web.pagination.PageLimit;
import com.reconcile.shared.web.pagination.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/discrepancies")
class DiscrepancyReadController {

    private static final Set<String> VALID_STATUSES = Set.of("OPEN", "RESOLVED");
    private static final Set<String> VALID_TYPES = Set.of("UNMATCHED", "AMBIGUOUS");

    private final DiscrepancyQueryPort queryPort;

    DiscrepancyReadController(DiscrepancyQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @GetMapping
    @Operation(summary = "List discrepancies (keyset-paginated, tenant-scoped)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of discrepancies"),
        @ApiResponse(responseCode = "400", description = "Unknown filter value, bad limit, or stale cursor")
    })
    PagedResponse<DiscrepancyListItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {

        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown status: " + status);
        }
        if (type != null && !VALID_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown type: " + type);
        }

        int resolvedLimit = PageLimit.resolve(limit);

        Map<String, String> filters = new HashMap<>();
        filters.put("status", status);
        filters.put("type", type);
        filters.put("runId", runId == null ? null : runId.toString());
        String filterHash = CursorCodec.filterHash(filters);

        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            Cursor decoded = CursorCodec.decode(cursor, filterHash);
            cursorCreatedAt = decoded.sortKey();
            cursorId = decoded.id();
        }

        DiscrepancyListQuery query =
                new DiscrepancyListQuery(status, type, runId, cursorCreatedAt, cursorId, resolvedLimit + 1);
        List<DiscrepancyListRow> rows = queryPort.list(query);

        boolean hasMore = rows.size() > resolvedLimit;
        List<DiscrepancyListRow> page = hasMore ? rows.subList(0, resolvedLimit) : rows;

        String nextCursor = null;
        if (hasMore) {
            DiscrepancyListRow last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.createdAt(), last.id(), filterHash);
        }

        List<DiscrepancyListItem> items =
                page.stream().map(DiscrepancyListItem::from).toList();
        return PagedResponse.of(items, nextCursor, hasMore);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get discrepancy detail, including cluster/ambiguity context")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Discrepancy detail"),
        @ApiResponse(responseCode = "404", description = "Not found in the caller's tenant")
    })
    DiscrepancyDetail detail(@PathVariable UUID id) {
        DiscrepancyForResolution discrepancy = queryPort
                .loadDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "discrepancy not found"));

        BigDecimal amount = null;
        String currency = null;
        if (discrepancy.unmatchedEntryId() != null) {
            EntryMoney money = queryPort
                    .findEntryMoney(discrepancy.unmatchedEntryId().value())
                    .orElse(null);
            if (money != null) {
                amount = money.amount();
                currency = money.currency();
            }
        }

        List<UUID> clusterMembers = discrepancy.cluster() == null
                ? List.of()
                : discrepancy.cluster().members().stream()
                        .map(LedgerEntryId::value)
                        .toList();

        return new DiscrepancyDetail(
                discrepancy.id(),
                discrepancy.matchRunId().value(),
                discrepancy.type(),
                discrepancy.status(),
                discrepancy.unmatchedEntryId() == null
                        ? null
                        : discrepancy.unmatchedEntryId().value(),
                amount,
                currency,
                clusterMembers);
    }
}
