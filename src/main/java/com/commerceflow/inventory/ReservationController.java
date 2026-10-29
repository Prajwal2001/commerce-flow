package com.commerceflow.inventory;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/{productId}/reservations")
public class ReservationController implements ReservationApi {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Override
    public ResponseEntity<ReservationResponse> reserve(@PathVariable UUID productId, @Valid @RequestBody ReserveStockRequest request) {
        ReservationResponse response = reservationService.reserve(productId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reservationId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{reservationId}")
    @Override
    public ReservationResponse get(@PathVariable UUID productId, @PathVariable UUID reservationId) {
        return reservationService.get(productId, reservationId);
    }

    @PostMapping("/{reservationId}/confirm")
    @Override
    public ResponseEntity<ReservationResponse> confirm(@PathVariable UUID productId, @PathVariable UUID reservationId) {
        ReservationResponse reservationResponse = reservationService.confirm(productId, reservationId);

        return ResponseEntity.ok(reservationResponse);
    }

    @PostMapping("/{reservationId}/release")
    @Override
    public ResponseEntity<ReservationResponse> release(@PathVariable UUID productId, @PathVariable UUID reservationId) {
        ReservationResponse reservationResponse = reservationService.release(productId, reservationId);

        return ResponseEntity.ok(reservationResponse);
    }


}
