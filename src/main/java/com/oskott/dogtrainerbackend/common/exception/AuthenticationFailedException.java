package com.oskott.dogtrainerbackend.common.exception;

/**
 * Thrown when authentication credentials are invalid or a token is invalid/expired.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
