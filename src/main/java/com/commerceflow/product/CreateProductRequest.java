package com.commerceflow.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(@NotBlank @Size(max = 64) String sku,
                                   @NotBlank @Size(max = 255) String name,
                                   @Size(max = 2000) String description,
                                   @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price) {
}
