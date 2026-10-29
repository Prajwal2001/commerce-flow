package com.commerceflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<StockItem, UUID> {
    /** flush first so pending changes are not lost; clear afterwards so no stale StockItem stays in the persistence context. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE StockItem si SET si.available = si.available - :qty, si.version = si.version + 1 WHERE si.productId = :productId AND si.available >= :qty")
    int updateStock(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE StockItem  si SET si.available = si.available + :qty, si.version = si.version + 1 WHERE si.productId = :productId")
    int updatedResturnedStock(@Param("productId") UUID productId, @Param("qty") int qty);
}