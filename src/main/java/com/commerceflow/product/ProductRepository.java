package com.commerceflow.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);

    /** One atomic statement: the row count tells whether this call actually deleted the product. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Product p where p.id = :id")
    int deleteProductById(@Param("id") UUID id);
}
