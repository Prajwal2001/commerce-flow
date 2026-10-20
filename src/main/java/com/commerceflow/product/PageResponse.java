package com.commerceflow.product;

import java.util.List;

/** Stable JSON shape for a page of results, so the API does not expose Spring Data's PageImpl internals. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
