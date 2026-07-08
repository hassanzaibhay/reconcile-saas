package com.reconcile.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Emits a uniform {@link com.reconcile.shared.error.ApiError} 403 instead of Spring Security's default empty body. */
@Component
class ApiErrorAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    ApiErrorAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        ApiErrorHttpWriter.write(response, mapper, 403, ErrorCode.FORBIDDEN, "Access denied");
    }
}
