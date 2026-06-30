package com.reconcile.reconciliation.domain;

import com.reconcile.shared.domain.Money;
import java.math.BigDecimal;

public enum MatchingAxis {
    SUM_TO_ZERO,
    DIFFERENCE;

    public BigDecimal amountDiff(Money a, Money b) {
        return switch (this) {
            case SUM_TO_ZERO -> a.add(b).amount().abs();
            case DIFFERENCE -> a.subtract(b).amount().abs();
        };
    }

    public boolean isExactMatch(Money a, Money b) {
        return amountDiff(a, b).compareTo(BigDecimal.ZERO) == 0;
    }
}
