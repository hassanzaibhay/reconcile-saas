package com.reconcile.reconciliation.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.reconciliation.application.DiscrepancyForResolution;
import com.reconcile.reconciliation.application.ResolutionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("DiscrepancyResolutionController — unknown discrepancy type")
class DiscrepancyResolutionControllerTest {

    private final ResolutionService resolutionService = Mockito.mock(ResolutionService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new DiscrepancyResolutionController(resolutionService, new ObjectMapper()))
            .build();

    @Test
    @DisplayName("type outside {UNMATCHED, AMBIGUOUS} maps to 500 with a structured ApiError, not a whitelabel page")
    void unknownTypeMapsToStructured500() throws Exception {
        UUID discrepancyId = UUID.randomUUID();
        DiscrepancyForResolution discrepancy =
                new DiscrepancyForResolution(discrepancyId, null, "BOGUS", null, null, "OPEN");
        when(resolutionService.find(any())).thenReturn(Optional.of(discrepancy));

        mockMvc.perform(post("/api/v1/discrepancies/{id}/resolve", discrepancyId)
                        .principal(() -> "operator@test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("unexpected discrepancy state"));
    }
}
