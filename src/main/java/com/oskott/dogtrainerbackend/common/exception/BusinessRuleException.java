package com.oskott.dogtrainerbackend.common.exception;

/**
 * Thrown for business-rule validation failures that don't fit standard bean validation
 * (e.g. duplicate email on registration, invalid state transitions).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
