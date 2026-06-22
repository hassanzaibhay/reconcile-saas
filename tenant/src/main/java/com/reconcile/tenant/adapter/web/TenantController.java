package com.reconcile.tenant.adapter.web;

import com.reconcile.tenant.application.TenantProvisioningService;
import com.reconcile.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
class TenantController {

    private final TenantProvisioningService provisioningService;

    TenantController(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Provision a new tenant")
    TenantResponse provision(@Valid @RequestBody ProvisionRequest request) {
        Tenant tenant = provisioningService.provision(request.slug());
        return new TenantResponse(
                tenant.id().toString(), tenant.slug(), tenant.status().name());
    }

    record ProvisionRequest(
            @NotBlank
            @Size(min = 2, max = 63)
            @Pattern(regexp = "[a-z0-9-]+", message = "slug must be lowercase alphanumeric with hyphens")
            String slug) {}

    record TenantResponse(String id, String slug, String status) {}
}
