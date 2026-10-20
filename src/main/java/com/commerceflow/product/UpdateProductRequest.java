package com.commerceflow.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Full replacement of the editable fields. The SKU is deliberately absent: it is immutable after creation. */
public record UpdateProductRequest(@NotBlank @Size(max = 255) String name,
                                   @Size(max = 2000) String description,
                                   @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price) {
}
