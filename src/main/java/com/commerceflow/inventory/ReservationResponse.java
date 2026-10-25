package com.commerceflow.inventory;

import java.util.UUID;

public record ReservationResponse(UUID id, UUID productId, int quantity, ReservationStatus status) {
    static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getStockItem().getProductId(), reservation.getQuantity(), reservation.getStatus());
    }
}
