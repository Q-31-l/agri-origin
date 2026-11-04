package com.agri.trace.sdk;

/**
 * 链交互异常
 */
public class ChainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChainException(String message) {
        super(message);
    }

    public ChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
