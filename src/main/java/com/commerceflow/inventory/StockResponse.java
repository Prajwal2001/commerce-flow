package com.commerceflow.inventory;

import java.util.UUID;

public record StockResponse(UUID productId, int available, Long version) {

    static StockResponse from(StockItem stockItem) {
        return new StockResponse(stockItem.getProductId(), stockItem.getAvailable(), stockItem.getVersion());
    }
}
