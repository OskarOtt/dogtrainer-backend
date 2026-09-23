package com.oskott.dogtrainerbackend.common.exception;

import org.springframework.http.HttpStatus;

public class AuthFlowException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthFlowException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
