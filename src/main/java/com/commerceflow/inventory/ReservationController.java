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
public class ReservationController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Reserve units of a product's stock",
            description = "Takes `quantity` units out of the available stock immediately. Never oversells: when several "
                    + "buyers compete for the last units, exactly as many succeed as there are units, and the rest get 409.")
    @ApiResponse(responseCode = "201", description = "Units reserved",
            headers = @Header(name = "Location", description = "URL of the reservation", schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid product id or quantity (must be at least 1)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No stock record for this product",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Fewer units are available than requested; nothing was reserved",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReservationResponse> reserve(@PathVariable UUID productId, @Valid @RequestBody ReserveStockRequest request) {
        ReservationResponse response = reservationService.reserve(productId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{reservationId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Get a reservation")
    @ApiResponse(responseCode = "200", description = "The reservation",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "An id is not a valid UUID",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such reservation for this product",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ReservationResponse get(@PathVariable UUID productId, @PathVariable UUID reservationId) {
        return reservationService.get(productId, reservationId);
    }
}
