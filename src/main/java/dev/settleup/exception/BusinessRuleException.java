package dev.settleup.exception;

/** A request that is well-formed but violates a domain rule (mapped to HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
