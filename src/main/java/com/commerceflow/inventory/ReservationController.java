package com.commerceflow.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/{productId}/reservations")
@Tag(name = "Inventory", description = "Stock levels per product. Inventory owns its own data and does not consult the catalog")
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
}
