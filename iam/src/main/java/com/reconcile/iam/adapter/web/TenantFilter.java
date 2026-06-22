package com.reconcile.iam.adapter.web;

import com.reconcile.shared.domain.TenantContext;
import com.reconcile.shared.domain.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code tid} claim from the authenticated JWT and sets {@link TenantContext}.
 * Runs after {@code BearerTokenAuthenticationFilter}. Unauthenticated requests (actuator,
 * openapi) skip tenant resolution — any data-access path on those requests will throw
 * {@link com.reconcile.shared.domain.MissingTenantException}.
 */
@Component
@Order(101)
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            var auth = request.getUserPrincipal();
            if (auth instanceof JwtAuthenticationToken jwt) {
                String tid = jwt.getToken().getClaimAsString("tid");
                if (tid != null && !tid.isBlank()) {
                    TenantContext.set(TenantId.of(UUID.fromString(tid)));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
