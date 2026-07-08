package com.reconcile.reconciliation.application;

import java.math.BigDecimal;

public record EntryMoney(BigDecimal amount, String currency) {}
