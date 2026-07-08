package com.reconcile.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Emits a uniform {@link com.reconcile.shared.error.ApiError} 401 instead of Spring Security's default empty body. */
@Component
class ApiErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    ApiErrorAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ApiErrorHttpWriter.write(response, mapper, 401, ErrorCode.UNAUTHENTICATED, "Authentication required");
    }
}
