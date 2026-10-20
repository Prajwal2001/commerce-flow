package com.commerceflow.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"sku": "KB-001", "name": "Keyboard", "description": "Mechanical", "price": 79.99}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleResponse(UUID id) {
        return new ProductResponse(id, "KB-001", "Keyboard", "Mechanical", new BigDecimal("79.99"), Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.create(any(CreateProductRequest.class))).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sku").value("KB-001"))
                .andExpect(jsonPath("$.price").value(79.99));
    }

    @Test
    void createWithInvalidFieldsReturns400ListingEachField() throws Exception {
        String body = """
                {"sku": "", "name": "Keyboard", "price": -5}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").doesNotExist());

        verifyNoInteractions(productService);
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\": "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void createWithDuplicateSkuReturns409() throws Exception {
        when(productService.create(any(CreateProductRequest.class)))
                .thenThrow(new DuplicateSkuException("Product with SKU 'KB-001' already exists"));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate SKU"))
                .andExpect(jsonPath("$.detail").value("Product with SKU 'KB-001' already exists"));
    }

    @Test
    void getReturnsProduct() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.get(id)).thenReturn(sampleResponse(id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void getMissingProductReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.get(id)).thenThrow(new ProductNotFoundException("Product not found: " + id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    void listReturnsProducts() throws Exception {
        when(productService.list()).thenReturn(List.of(sampleResponse(UUID.randomUUID()), sampleResponse(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
