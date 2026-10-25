package com.commerceflow.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;

    public ReservationService(ReservationRepository reservationRepository, StockRepository stockRepository) {
        this.reservationRepository = reservationRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public ReservationResponse reserve(UUID productId, ReserveStockRequest request) {

        StockItem stockItem = stockRepository.findById(productId).orElseThrow(() -> new StockNotFoundException("stock not found for product: " + productId));

        int updated = stockRepository.updateStock(productId, request.quantity());

        if (updated <= 0) {
            throw new InsufficientStockException("quantity must be less than or equal to available");
        }

        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(stockItem, request.quantity(), ReservationStatus.RESERVED));
        System.out.println(reservation);

        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID productId, UUID reservationId) {
        return ReservationResponse.from(
                reservationRepository.findByStockItem_ProductIdAndId(productId, reservationId).orElseThrow(
                        () -> new ReservationNotFoundException("Reservation not found for reservation id: " + reservationId)));
    }
}
