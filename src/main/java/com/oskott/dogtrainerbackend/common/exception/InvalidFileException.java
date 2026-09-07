package com.oskott.dogtrainerbackend.common.exception;

/**
 * Thrown when an uploaded file's content type or size fails server-side validation.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
