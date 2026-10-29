package com.commerceflow.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * OpenAPI documentation for {@link ReservationController}, kept here so the controller stays readable.
 * Only documentation lives on this interface; the request mappings and parameter bindings stay on the controller.
 */
@Tag(name = "Inventory", description = "Stock levels per product. Inventory owns its own data and does not consult the catalog")
public interface ReservationApi {

    @Operation(summary = "Reserve units of a product's stock",
            description = "Takes `quantity` units out of the available stock immediately. Never oversells: when several "
                    + "buyers compete for the last units, exactly as many succeed as there are units, and the rest get 409.")
    @ApiResponse(responseCode = "201", description = "Units reserved",
            headers = @Header(name = "Location", description = "URL of the reservation", schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid product id or quantity (must be at least 1)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No stock record for this product",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Fewer units are available than requested; nothing was reserved",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ReservationResponse> reserve(UUID productId, ReserveStockRequest request);

    @Operation(summary = "Get a reservation")
    @ApiResponse(responseCode = "200", description = "The reservation",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "An id is not a valid UUID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such reservation for this product",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ReservationResponse get(UUID productId, UUID reservationId);

    @Operation(summary = "Confirm a reservation",
            description = "Marks the reservation `CONFIRMED`; the units stay deducted from the stock. Confirming again is a no-op "
                    + "that returns the current state. A released reservation cannot be confirmed.")
    @ApiResponse(responseCode = "200", description = "The confirmed reservation",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "An id is not a valid UUID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such reservation for this product",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The reservation was already released",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ReservationResponse> confirm(UUID productId, UUID reservationId);

    @Operation(summary = "Release a reservation",
            description = "Marks the reservation `RELEASED` and returns its units to the available stock, exactly once even if "
                    + "called repeatedly or concurrently. Releasing again is a no-op that returns the current state. A confirmed "
                    + "reservation cannot be released.")
    @ApiResponse(responseCode = "200", description = "The released reservation",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "An id is not a valid UUID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such reservation for this product",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The reservation was already confirmed",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ReservationResponse> release(UUID productId, UUID reservationId);
}
