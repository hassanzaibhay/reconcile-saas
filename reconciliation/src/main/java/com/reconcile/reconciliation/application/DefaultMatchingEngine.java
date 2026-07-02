package com.reconcile.reconciliation.application;

import com.reconcile.ledger.domain.LedgerEntry;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uniform graph-partition matching engine.
 *
 * <p>For each rule (in list order) the engine:
 *
 * <ol>
 *   <li>Freezes the current working pool.
 *   <li>Calls {@code rule.neighbors()} for every node against the frozen snapshot to build an
 *       undirected adjacency graph.
 *   <li>Finds connected components via BFS (start nodes in UUID sort order).
 *   <li>Partitions by component size: size-1 → stays in pool for the next pass; size-2 →
 *       matched pair; size-≥3 → ambiguous cluster, all removed.
 * </ol>
 *
 * <p>Exact precedence follows from pass order: the exact rule runs first and removes its
 * matched/clustered entries before the tolerance rule sees the residual.
 */
@Service
public class DefaultMatchingEngine implements MatchingEngine {

    private final AuditDecisionRepository auditDecisionRepository;
    private final ApplicationEventPublisher events;

    public DefaultMatchingEngine(AuditDecisionRepository auditDecisionRepository, ApplicationEventPublisher events) {
        this.auditDecisionRepository = auditDecisionRepository;
        this.events = events;
    }

    @Override
    @Transactional
    public MatchRunResult run(MatchRunId runId, List<MatchRule> rules, Iterable<LedgerEntry> entries) {
        List<LedgerEntry> sortedEntries = new ArrayList<>();
        entries.forEach(sortedEntries::add);
        sortedEntries.sort(Comparator.comparing(e -> e.id().value()));
        LinkedHashSet<LedgerEntry> workingPool = new LinkedHashSet<>(sortedEntries);

        List<MatchPair> matches = new ArrayList<>();
        List<AmbiguousCluster> clusters = new ArrayList<>();
        List<Discrepancy.Unmatched> discrepancies = new ArrayList<>();

        for (MatchRule rule : rules) {
            runPass(rule, workingPool, runId, matches, clusters);
        }

        for (LedgerEntry e : workingPool) {
            discrepancies.add(Discrepancy.of(runId, e.id()));
            recordDecision(runId, "NONE", e.id(), "UNMATCHED", null);
        }

        MatchRunResult result = new MatchRunResult(runId, matches, clusters, discrepancies);
        events.publishEvent(new MatchRunCompletedEvent(runId));
        return result;
    }

    private void runPass(
            MatchRule rule,
            LinkedHashSet<LedgerEntry> workingPool,
            MatchRunId runId,
            List<MatchPair> matches,
            List<AmbiguousCluster> clusters) {

        List<LedgerEntry> frozen = List.copyOf(workingPool);

        // Build adjacency map over frozen pool
        Map<LedgerEntryId, List<LedgerEntry>> adjacency = new LinkedHashMap<>();
        for (LedgerEntry node : frozen) {
            adjacency.put(node.id(), rule.neighbors(node, frozen));
        }

        // BFS connected components; start nodes in frozen (sort) order
        Set<LedgerEntryId> visited = new LinkedHashSet<>();
        List<List<LedgerEntry>> components = new ArrayList<>();

        for (LedgerEntry start : frozen) {
            if (visited.contains(start.id())) continue;

            List<LedgerEntry> component = new ArrayList<>();
            Queue<LedgerEntry> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start.id());

            while (!queue.isEmpty()) {
                LedgerEntry node = queue.poll();
                component.add(node);
                for (LedgerEntry neighbor : adjacency.get(node.id())) {
                    if (!visited.contains(neighbor.id())) {
                        visited.add(neighbor.id());
                        queue.add(neighbor);
                    }
                }
            }

            component.sort(Comparator.comparing(e -> e.id().value()));
            components.add(component);
        }

        // Partition by size; process in order of min-member UUID for determinism
        components.sort(Comparator.comparing(c -> c.get(0).id().value()));

        for (List<LedgerEntry> comp : components) {
            switch (comp.size()) {
                case 1 -> {
                    /* isolated — leave in workingPool for the next pass */
                }
                case 2 -> {
                    LedgerEntry a = comp.get(0), b = comp.get(1);
                    workingPool.remove(a);
                    workingPool.remove(b);
                    matches.add(new MatchPair(a.id(), b.id(), rule.ruleId()));
                    recordDecision(runId, rule.ruleId(), a.id(), "MATCHED", null);
                    recordDecision(runId, rule.ruleId(), b.id(), "MATCHED", null);
                }
                default -> {
                    List<LedgerEntryId> memberIds =
                            comp.stream().map(LedgerEntry::id).toList();
                    comp.forEach(workingPool::remove);
                    clusters.add(new AmbiguousCluster(memberIds));
                    comp.forEach(m -> recordDecision(runId, rule.ruleId(), m.id(), "AMBIGUOUS", null));
                }
            }
        }
    }

    private void recordDecision(
            MatchRunId runId, String ruleId, LedgerEntryId entryId, String decision, String reason) {
        auditDecisionRepository.save(new AuditDecision(
                UUID.randomUUID(), runId, ruleId, entryId, decision, reason, Instant.now(), "SYSTEM"));
    }
}
