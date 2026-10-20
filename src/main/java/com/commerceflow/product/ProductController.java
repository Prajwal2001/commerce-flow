package com.commerceflow.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog: create products and read them back, one or a page at a time")
public class ProductController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final Set<String> SORTABLE_PROPERTIES = new TreeSet<>(Set.of("createdAt", "name", "price", "sku"));

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Create a product",
            description = "The SKU must be unique. Uniqueness is enforced by the database, so concurrent requests with the same SKU yield exactly one 201.")
    @ApiResponse(responseCode = "201", description = "Product created",
            headers = @Header(name = "Location", description = "URL of the new product", schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid or unreadable request body; `errors` lists each failing field",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A product with this SKU already exists",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    @ApiResponse(responseCode = "200", description = "The product",
            content = @Content(schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "400", description = "The id is not a valid UUID",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No product with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @GetMapping
    @Operation(summary = "List products, one page at a time", description = "Newest first unless `sort` says otherwise.")
    @ApiResponse(responseCode = "200", description = "One page of products with paging totals")
    @ApiResponse(responseCode = "400", description = "Invalid `page`, `size` or `sort`",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public PageResponse<ProductResponse> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "`property[,asc|desc]`; property is one of createdAt, name, price, sku")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return productService.list(pageable);
    }

    /** Parses "property[,direction]"; anything outside the whitelist is a client error, not a database error. */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE_PROPERTIES.contains(property)) {
            throw new InvalidSortException("Cannot sort by '" + property + "'; allowed: " + SORTABLE_PROPERTIES);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            String value = parts[1].trim();
            direction = Sort.Direction.fromOptionalString(value)
                    .orElseThrow(() -> new InvalidSortException("Invalid sort direction '" + value + "'; use asc or desc"));
        }
        return Sort.by(direction, property);
    }
}
