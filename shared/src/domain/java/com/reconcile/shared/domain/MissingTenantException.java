package com.reconcile.shared.domain;

public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}
