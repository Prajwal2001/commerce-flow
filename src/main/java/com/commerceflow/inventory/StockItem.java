package com.commerceflow.inventory;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

@Entity
@Table(name = "stock_item")
public class StockItem {

    @Id
    private UUID productId;

    @Column(nullable = false)
    @PositiveOrZero
    private int available;

    @Column(nullable = false)
    @Version
    private Long version;

    public StockItem() {
    }

    protected StockItem(UUID productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }

    public Long getVersion() {
        return version;
    }
}
