package com.commerceflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Optional<Reservation> findByProductIdAndId(UUID productId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.productId = :productId AND r.id = :id")
    Optional<Reservation> findByProductIdAndIdLocked(@Param("productId") UUID productId, @Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = 'RELEASED', r.version = r.version + 1 WHERE r.id = :id AND r.status = 'RESERVED' AND r.productId = :productId")
    int updateStatusToRelease(@Param("id") UUID id, @Param("productId") UUID productId);
}
