package com.reconcile.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ToleranceConfigDefaultsTest {

    @Test
    void defaultsReturnExpectedValues() {
        ToleranceConfig cfg = ToleranceConfig.defaults();

        assertThat(cfg.absoluteTolerance()).as("absolute tolerance").isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(cfg.percentageTolerance()).as("percentage tolerance").isEqualByComparingTo(new BigDecimal("0.001"));
        assertThat(cfg.maxDateDriftDays()).as("max date drift days").isEqualTo(0);
        assertThat(cfg.axis()).as("default axis").isEqualTo(MatchingAxis.SUM_TO_ZERO);
    }

    @Test
    void fourArgConstructorDefaultsAxisToSumToZero() {
        ToleranceConfig cfg = new ToleranceConfig(null, new BigDecimal("0.05"), new BigDecimal("0.002"), 2);

        assertThat(cfg.axis()).isEqualTo(MatchingAxis.SUM_TO_ZERO);
    }
}
