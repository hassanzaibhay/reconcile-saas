package com.reconcile.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconcile.shared.domain.MissingTenantException;
import com.reconcile.shared.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void failsClosedWhenContextUnset() {
        TenantContext.clear();
        assertThatThrownBy(TenantContext::current)
                .isInstanceOf(MissingTenantException.class)
                .hasMessageContaining("No tenant context set");
    }
}
