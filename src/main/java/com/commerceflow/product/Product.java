package com.commerceflow.product;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {}

    public Product(String sku, String name, String description, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = normalize(price);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void updateData(String name, String description, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.price = normalize(price);
    }

    /**
     * Prices are stored as numeric(12,2). Normalizing to scale 2 up front means the object we return
     * after saving matches what a later read returns. UNNECESSARY makes a value with more than two
     * decimals fail loudly instead of being silently rounded; request validation rejects those earlier.
     */
    private static BigDecimal normalize(BigDecimal price) {
        return price == null ? null : price.setScale(2, RoundingMode.UNNECESSARY);
    }
}
