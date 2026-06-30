package com.reconcile.reconciliation.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ToleranceConfigJpaRepository extends JpaRepository<ToleranceConfigEntity, Boolean> {}
