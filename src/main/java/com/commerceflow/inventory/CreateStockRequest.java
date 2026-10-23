package com.commerceflow.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** {@code productId} is only a reference: the catalog is a different module and is deliberately not consulted. */
public record CreateStockRequest(@NotNull UUID productId, @NotNull @PositiveOrZero Integer available) {
}
