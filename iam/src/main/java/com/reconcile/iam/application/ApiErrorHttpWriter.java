package com.reconcile.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcile.shared.error.ApiError;
import com.reconcile.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/** Writes an {@link ApiError} body for the Spring Security handlers that sit outside {@code @ControllerAdvice} reach. */
final class ApiErrorHttpWriter {

    private ApiErrorHttpWriter() {}

    static void write(HttpServletResponse response, ObjectMapper mapper, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), ApiError.of(status, code, message));
    }
}
