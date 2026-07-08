package com.reconcile.shared.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageLimitTest {

    @Test
    void nullRequestFallsBackToDefault() {
        assertThat(PageLimit.resolve(null)).isEqualTo(PageLimit.DEFAULT);
    }

    @Test
    void acceptsInRangeLimits() {
        assertThat(PageLimit.resolve(1)).isEqualTo(1);
        assertThat(PageLimit.resolve(PageLimit.MAX)).isEqualTo(PageLimit.MAX);
    }

    @Test
    void rejectsOutOfRangeLimits() {
        assertThatThrownBy(() -> PageLimit.resolve(0)).isInstanceOf(InvalidPageLimitException.class);
        assertThatThrownBy(() -> PageLimit.resolve(PageLimit.MAX + 1)).isInstanceOf(InvalidPageLimitException.class);
        assertThatThrownBy(() -> PageLimit.resolve(-5)).isInstanceOf(InvalidPageLimitException.class);
    }
}
