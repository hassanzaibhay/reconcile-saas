package com.reconcile.app.support;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Meta-annotation: provisions a UUID-named tenant schema in Testcontainers Postgres, sets
 * TenantContext before each test, drops schema and clears context after.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(TenantTestExtension.class)
public @interface TenantTest {}
