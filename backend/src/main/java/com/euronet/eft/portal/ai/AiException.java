package com.euronet.eft.portal.ai;

import org.springframework.http.HttpStatus;

/** An AI request that cannot be served; the message is safe to show to the user. */
public class AiException extends RuntimeException {

    private final HttpStatus status;

    public AiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static AiException disabled() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI features are switched off on this portal.");
    }
}
