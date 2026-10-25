package com.commerceflow.common;

import org.hibernate.exception.ConstraintViolationException;

/** Reads database constraint names out of exceptions, so services never parse driver messages. */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /**
     * True if {@code error} or any of its causes is Hibernate's violation of the constraint called {@code constraintName}.
     * Walks the whole cause chain because the wrapping depth can vary.
     */
    public static boolean isViolationOf(Throwable error, String constraintName) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && constraintName.equals(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
