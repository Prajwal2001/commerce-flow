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
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Stock levels per product. Inventory owns its own data and does not consult the catalog")
public class StockController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping
    @Operation(summary = "Create the stock record for a product",
            description = "One record per product. The product is not checked against the catalog.")
    @ApiResponse(responseCode = "201", description = "Stock created",
            headers = @Header(name = "Location", description = "URL of the stock record", schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = StockResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid or unreadable request body; `errors` lists each failing field",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Stock already exists for this product",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<StockResponse> create(@Valid @RequestBody CreateStockRequest request) {

        StockResponse response = stockService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.productId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get the stock record of a product")
    @ApiResponse(responseCode = "200", description = "The stock record",
            content = @Content(schema = @Schema(implementation = StockResponse.class)))
    @ApiResponse(responseCode = "400", description = "The product id is not a valid UUID",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No stock record for this product",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public StockResponse get(@PathVariable UUID productId) {
        return stockService.get(productId);
    }
}
