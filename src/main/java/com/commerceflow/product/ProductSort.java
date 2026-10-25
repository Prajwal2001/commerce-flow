package com.commerceflow.product;

import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/** Parses the {@code sort} query parameter ({@code property[,asc|desc]}) against a whitelist of sortable properties. */
final class ProductSort {

    static final Set<String> SORTABLE_PROPERTIES = new TreeSet<>(Set.of("createdAt", "name", "price", "sku"));

    private ProductSort() {
    }

    /** Anything outside the whitelist is a client error, not a database error, so it is rejected here. */
    static Sort parse(String sort) {
        String[] parts = sort.split(",");
        if (parts.length > 2) {
            throw new InvalidSortException("Invalid sort '" + sort + "'; use property[,asc|desc]");
        }
        String property = parts[0].trim();
        if (!SORTABLE_PROPERTIES.contains(property)) {
            throw new InvalidSortException("Cannot sort by '" + property + "'; allowed: " + SORTABLE_PROPERTIES);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String value = parts[1].trim();
            direction = Sort.Direction.fromOptionalString(value)
                    .orElseThrow(() -> new InvalidSortException("Invalid sort direction '" + value + "'; use asc or desc"));
        }
        return Sort.by(direction, property);
    }
}
