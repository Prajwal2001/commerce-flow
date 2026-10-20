package com.commerceflow.product;

public class ProductVersionConflictException extends RuntimeException {
    public ProductVersionConflictException(String message) {
        super(message);
    }
}
