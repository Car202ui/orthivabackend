package com.orthiva.core.shared.web;

import org.springframework.http.HttpStatus;

/** Base for errors that map 1:1 to an HTTP status with a safe, user-facing message. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    /** Stable machine-readable code the frontend can translate (i18n). */
    public String code() {
        return code;
    }

    public static DomainException notFound(String what) {
        return new DomainException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
    }

    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}
