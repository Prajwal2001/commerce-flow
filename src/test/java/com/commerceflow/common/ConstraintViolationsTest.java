package com.commerceflow.common;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationsTest {

    private static ConstraintViolationException violationOf(String name) {
        return new ConstraintViolationException("could not execute statement", new SQLException("violation"), name);
    }

    @Test
    void findsTheConstraintWhenItIsTheDirectCause() {
        var error = new DataIntegrityViolationException("wrapped", violationOf("uq_products_sku"));

        assertThat(ConstraintViolations.isViolationOf(error, "uq_products_sku")).isTrue();
    }

    @Test
    void findsTheConstraintWhenItIsWrappedDeeper() {
        var error = new DataIntegrityViolationException("outer",
                new RuntimeException("middle", new RuntimeException("inner", violationOf("pk_stock_item"))));

        assertThat(ConstraintViolations.isViolationOf(error, "pk_stock_item")).isTrue();
    }

    @Test
    void aDifferentConstraintDoesNotMatch() {
        var error = new DataIntegrityViolationException("wrapped", violationOf("positive_available"));

        assertThat(ConstraintViolations.isViolationOf(error, "pk_stock_item")).isFalse();
    }

    @Test
    void anErrorWithoutAHibernateViolationDoesNotMatch() {
        assertThat(ConstraintViolations.isViolationOf(new DataIntegrityViolationException("no cause"), "uq_products_sku")).isFalse();
    }

    @Test
    void theMessageTextIsNeverUsedToMatch() {
        // Driver text can mention a constraint name too; only the structured name counts.
        var error = new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_products_sku\"");

        assertThat(ConstraintViolations.isViolationOf(error, "uq_products_sku")).isFalse();
    }
}
