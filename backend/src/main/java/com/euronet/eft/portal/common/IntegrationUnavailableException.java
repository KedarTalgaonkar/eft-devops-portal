package com.euronet.eft.portal.common;

/** An external tool (Jenkins, GitHub, ...) could not be reached or answered with an error. */
public class IntegrationUnavailableException extends RuntimeException {

    public IntegrationUnavailableException(String tool, String message, Throwable cause) {
        super(tool + ": " + message, cause);
    }
}
