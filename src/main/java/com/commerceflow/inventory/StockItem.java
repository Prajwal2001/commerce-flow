package com.commerceflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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


    //version is of type Long and not long (primitive) as the productId is not generated, the version null check becomes critical for deciding INSERT or UPDATE
    @Column(nullable = false)
    @Version
    private Long version;

    protected StockItem() {
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
