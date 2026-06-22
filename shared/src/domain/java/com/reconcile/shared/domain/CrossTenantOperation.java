package com.reconcile.shared.domain;

/**
 * Sealed marker for approved cross-tenant operations. Initially empty — no cross-tenant use cases
 * are approved. To add one: extend this sealed interface with a new permitted type, add an audit
 * template, and record it in docs/rules/multitenancy.md under "Approved cross-tenant
 * operations".
 */
public sealed interface CrossTenantOperation permits /* none yet */ CrossTenantOperation.None {

    /** Placeholder to satisfy the sealed permits clause. Remove when a real case is added. */
    record None() implements CrossTenantOperation {}
}
