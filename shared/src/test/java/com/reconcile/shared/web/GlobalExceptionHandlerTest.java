package com.reconcile.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconcile.shared.error.ApiError;
import com.reconcile.shared.error.ErrorCode;
import com.reconcile.shared.web.pagination.InvalidCursorException;
import com.reconcile.shared.web.pagination.InvalidPageLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusPassesStatusThroughAndDerivesCode() {
        assertMapping(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        assertMapping(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertMapping(HttpStatus.CONFLICT, ErrorCode.CONFLICT);
        assertMapping(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void other4xxNeverBecomesInternalError() {
        // Delta 2: a client error must not be labeled a server fault.
        ApiError body = handler.onResponseStatus(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down"))
                .getBody();
        assertThat(body.status()).isEqualTo(429);
        assertThat(body.code()).isEqualTo(ErrorCode.CLIENT_ERROR);
        assertThat(body.code()).isNotEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void badPaginationMapsTo400() {
        assertThat(handler.onBadPagination(new InvalidCursorException("bad"))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(handler.onBadPagination(new InvalidPageLimitException("bad"))
                        .getBody()
                        .code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void everyEnvelopeCarriesATraceId() {
        ApiError body = handler.onResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "x"))
                .getBody();
        assertThat(body.traceId()).isNotBlank();
    }

    private void assertMapping(HttpStatus status, ErrorCode expected) {
        ApiError body = handler.onResponseStatus(new ResponseStatusException(status, "reason"))
                .getBody();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.code()).isEqualTo(expected);
        assertThat(body.message()).isEqualTo("reason");
    }
}
