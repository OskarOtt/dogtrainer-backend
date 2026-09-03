package com.oskott.dogtrainerbackend.common.exception;

/**
 * Thrown when the authenticated user attempts to access a resource they do not own.
 */
public class AccessDeniedForResourceException extends RuntimeException {

    public AccessDeniedForResourceException(String message) {
        super(message);
    }
}
