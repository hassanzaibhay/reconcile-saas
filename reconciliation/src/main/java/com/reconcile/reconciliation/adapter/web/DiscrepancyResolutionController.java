package com.reconcile.reconciliation.adapter.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.ledger.domain.LedgerEntryId;
import com.reconcile.reconciliation.application.DiscrepancyForResolution;
import com.reconcile.reconciliation.application.ResolutionService;
import com.reconcile.reconciliation.domain.InvalidResolutionException;
import com.reconcile.reconciliation.domain.Pairing;
import com.reconcile.shared.error.ApiError;
import com.reconcile.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/discrepancies")
class DiscrepancyResolutionController {

    /**
     * Spring Boot's autoconfigured ObjectMapper (via {@code Jackson2ObjectMapperBuilder}) disables
     * FAIL_ON_UNKNOWN_PROPERTIES by default, and there is no per-DTO Jackson annotation that can
     * re-enable strictness on top of a lenient global mapper — {@code @JsonIgnoreProperties} can only
     * loosen, never tighten. So this endpoint parses its own body with a dedicated strict mapper
     * instead of relying on {@code @RequestBody} binding. The strict mapper is a {@code .copy()} of
     * the app's configured bean so it inherits every registered module and custom (de)serializer
     * (JavaTime, Money, etc.) instead of drifting from the global mapper's behavior.
     */
    private final ObjectMapper strictMapper;

    private final ResolutionService resolutionService;

    DiscrepancyResolutionController(ResolutionService resolutionService, ObjectMapper objectMapper) {
        this.resolutionService = resolutionService;
        this.strictMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve an open discrepancy (accept-as-reviewed or partition an ambiguous cluster)")
    ResolveResponse resolve(@PathVariable UUID id, @RequestBody String rawBody, Principal principal) {
        ResolveRequest request = parseStrict(rawBody);
        DiscrepancyForResolution discrepancy = resolutionService
                .find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "discrepancy not found"));
        String resolvedBy = principal.getName();

        switch (discrepancy.type()) {
            case "UNMATCHED" -> {
                if (!request.pairings().isEmpty() || !request.leftUnmatched().isEmpty()) {
                    throw new DiscrepancyTypeMismatchException(
                            "UNMATCHED discrepancy cannot carry pairings or leftUnmatched");
                }
                resolutionService.resolveUnmatched(id, resolvedBy);
            }
            case "AMBIGUOUS" -> {
                List<Pairing> pairings = request.pairings().stream()
                        .map(p -> new Pairing(LedgerEntryId.of(p.a()), LedgerEntryId.of(p.b())))
                        .toList();
                List<LedgerEntryId> leftUnmatched =
                        request.leftUnmatched().stream().map(LedgerEntryId::of).toList();
                resolutionService.resolveCluster(discrepancy, pairings, leftUnmatched, resolvedBy);
            }
            default -> throw new IllegalStateException("unknown discrepancy type: " + discrepancy.type());
        }

        return new ResolveResponse("RESOLVED");
    }

    private ResolveRequest parseStrict(String rawBody) {
        try {
            String body = (rawBody == null || rawBody.isBlank()) ? "{}" : rawBody;
            return strictMapper.readValue(body, ResolveRequest.class);
        } catch (JsonProcessingException e) {
            throw new MalformedResolveRequestException(e.getOriginalMessage());
        }
    }

    @ExceptionHandler(MalformedResolveRequestException.class)
    ResponseEntity<ApiError> onMalformedBody(MalformedResolveRequestException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(DiscrepancyTypeMismatchException.class)
    ResponseEntity<ApiError> onTypeMismatch(DiscrepancyTypeMismatchException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(422, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(InvalidResolutionException.class)
    ResponseEntity<ApiError> onInvalidResolution(InvalidResolutionException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiError.of(422, ErrorCode.VALIDATION_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onAvailabilityConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, ErrorCode.CONFLICT, "one or more entries are already matched"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> onAlreadyResolved(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        409,
                        ErrorCode.CONFLICT,
                        "discrepancy is not open (already resolved or concurrently modified)"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> onUnexpectedState(IllegalStateException ex) {
        return ResponseEntity.internalServerError()
                .body(ApiError.of(500, ErrorCode.INTERNAL_ERROR, "unexpected discrepancy state"));
    }

    record ResolveRequest(List<PairingDto> pairings, List<UUID> leftUnmatched) {
        ResolveRequest {
            pairings = pairings != null ? pairings : List.of();
            leftUnmatched = leftUnmatched != null ? leftUnmatched : List.of();
        }
    }

    record PairingDto(UUID a, UUID b) {}

    record ResolveResponse(String status) {}
}
