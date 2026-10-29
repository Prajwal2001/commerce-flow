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

        if (updated == 0) {
            throw new InsufficientStockException("quantity must be less than or equal to available");
        }

        Reservation reservation = reservationRepository.saveAndFlush(Reservation.reserve(stockItem.getProductId(), request.quantity()));

        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID productId, UUID reservationId) {
        return ReservationResponse.from(
                reservationRepository.findByProductIdAndId(productId, reservationId).orElseThrow(
                        () -> new ReservationNotFoundException("Reservation not found for reservation id: " + reservationId)));
    }

    @Transactional
    public ReservationResponse confirm(UUID productId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByProductIdAndIdLocked(productId, reservationId).orElseThrow(() -> new ReservationNotFoundException("Reservation not found for reservation id: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new InvalidReservationStateException("Invalid change of reservation status from " + ReservationStatus.RELEASED + " to " + ReservationStatus.CONFIRMED);
        }

        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            reservation.confirm();
            return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
        }

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse release(UUID productId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByProductIdAndIdLocked(productId, reservationId).orElseThrow(() -> new ReservationNotFoundException("Reservation not found for reservation id: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new InvalidReservationStateException("Invalid change of reservation status from " + ReservationStatus.CONFIRMED + " to " + ReservationStatus.RELEASED);
        }

        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            reservation.release();
            stockRepository.updatedResturnedStock(productId, reservation.getQuantity());
        }

        return ReservationResponse.from(reservation);
    }
}
