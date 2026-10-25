package com.commerceflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<StockItem, UUID> {
    @Modifying
    @Query("update StockItem si set si.available = si.available - :qty, si.version = si.version + 1 where si.productId = :productId and si.available >= :qty")
    int updateStock(@Param("productId") UUID productId, @Param("qty") int qty);
}